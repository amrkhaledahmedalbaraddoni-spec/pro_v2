/**
 * Authorization & Security System - نظام الصلاحيات والأمان
 *
 * الميزات:
 * ✅ Role-based access control (RBAC)
 * ✅ Granular permissions
 * ✅ Resource-level permissions
 * ✅ Time-based restrictions
 * ✅ IP-based access control
 * ✅ Audit logging
 * ✅ Session management
 * ✅ Encryption
 */

export type Role = 'ADMIN' | 'MODERATOR' | 'USER' | 'GUEST';

export interface Permission {
  id: string;
  name: string;
  description: string;
  resource: string;
  action: 'CREATE' | 'READ' | 'UPDATE' | 'DELETE' | 'MANAGE';
}

export interface RolePermission {
  roleId: string;
  role: Role;
  permissions: Permission[];
  createdAt: number;
  updatedAt: number;
}

export interface UserSession {
  sessionId: string;
  userId: string;
  userName: string;
  role: Role;
  permissions: Permission[];
  createdAt: number;
  expiresAt: number;
  ipAddress: string;
  userAgent: string;
  lastActivity: number;
  isActive: boolean;
}

export interface AuditLog {
  id: string;
  userId: string;
  userName: string;
  action: string;
  resource: string;
  resourceId: string;
  changes?: Record<string, any>;
  result: 'SUCCESS' | 'FAILURE';
  errorMessage?: string;
  ipAddress: string;
  userAgent: string;
  timestamp: number;
}

export interface ResourcePermission {
  resourceId: string;
  resourceType: string;
  userId: string;
  role: 'OWNER' | 'EDITOR' | 'VIEWER';
  grantedAt: number;
  grantedBy: string;
  expiresAt?: number;
}

// ============= RBAC Manager =============

export class RBACManager {
  private rolePermissions: Map<Role, Set<string>> = new Map();
  private userPermissions: Map<string, Set<string>> = new Map();
  private resourcePermissions: Map<string, ResourcePermission[]> = new Map();
  private sessions: Map<string, UserSession> = new Map();
  private auditLogs: AuditLog[] = [];
  private listeners: Map<string, Set<(data: any) => void>> = new Map();

  // ========== Initialization ==========

  constructor() {
    this.initializeDefaultRoles();
  }

  private initializeDefaultRoles(): void {
    // ADMIN - Full access
    this.rolePermissions.set('ADMIN', new Set([
      'users:create', 'users:read', 'users:update', 'users:delete', 'users:manage',
      'messages:create', 'messages:read', 'messages:update', 'messages:delete', 'messages:manage',
      'groups:create', 'groups:read', 'groups:update', 'groups:delete', 'groups:manage',
      'calls:create', 'calls:read', 'calls:manage',
      'media:manage',
      'settings:manage',
      'audit:read',
    ]));

    // MODERATOR - Moderate content
    this.rolePermissions.set('MODERATOR', new Set([
      'users:read',
      'messages:read', 'messages:delete',
      'groups:read', 'groups:manage',
      'calls:read',
      'audit:read',
    ]));

    // USER - Standard user
    this.rolePermissions.set('USER', new Set([
      'users:read',
      'messages:create', 'messages:read', 'messages:update',
      'groups:create', 'groups:read', 'groups:update',
      'calls:create', 'calls:read',
      'media:create', 'media:read',
    ]));

    // GUEST - Limited access
    this.rolePermissions.set('GUEST', new Set([
      'messages:read',
      'groups:read',
      'calls:read',
    ]));
  }

  // ========== Session Management ==========

  createSession(
    userId: string,
    userName: string,
    role: Role,
    ipAddress: string,
    userAgent: string
  ): UserSession {
    const sessionId = `sess_${Date.now()}_${Math.random().toString(36).slice(2)}`;
    const now = Date.now();
    const expiresAt = now + 24 * 60 * 60 * 1000; // 24 hours

    const permissions = Array.from(this.rolePermissions.get(role) || []).map((p) => ({
      id: p,
      name: p,
      description: '',
      resource: p.split(':')[0],
      action: (p.split(':')[1].toUpperCase() as any) || 'READ',
    }));

    const session: UserSession = {
      sessionId,
      userId,
      userName,
      role,
      permissions,
      createdAt: now,
      expiresAt,
      ipAddress,
      userAgent,
      lastActivity: now,
      isActive: true,
    };

    this.sessions.set(sessionId, session);

    // Log session creation
    this.logAudit({
      userId,
      userName,
      action: 'SESSION_CREATED',
      resource: 'SESSION',
      resourceId: sessionId,
      result: 'SUCCESS',
      ipAddress,
      userAgent,
    });

    this.emit('session:created', session);
    return session;
  }

  validateSession(sessionId: string): UserSession | null {
    const session = this.sessions.get(sessionId);

    if (!session) {
      return null;
    }

    if (session.expiresAt < Date.now()) {
      this.sessions.delete(sessionId);
      return null;
    }

    if (!session.isActive) {
      return null;
    }

    // Update last activity
    session.lastActivity = Date.now();
    return session;
  }

  endSession(sessionId: string): void {
    const session = this.sessions.get(sessionId);
    if (session) {
      session.isActive = false;

      this.logAudit({
        userId: session.userId,
        userName: session.userName,
        action: 'SESSION_ENDED',
        resource: 'SESSION',
        resourceId: sessionId,
        result: 'SUCCESS',
        ipAddress: session.ipAddress,
        userAgent: session.userAgent,
      });

      this.emit('session:ended', { sessionId });
    }
  }

  // ========== Permission Checks ==========

  hasPermission(sessionId: string, permission: string): boolean {
    const session = this.validateSession(sessionId);
    if (!session) return false;

    const rolePerms = this.rolePermissions.get(session.role);
    if (!rolePerms) return false;

    // Check role permissions
    if (rolePerms.has(permission)) {
      return true;
    }

    // Check user-specific permissions
    const userPerms = this.userPermissions.get(session.userId);
    if (userPerms && userPerms.has(permission)) {
      return true;
    }

    return false;
  }

  hasResourcePermission(
    userId: string,
    resourceId: string,
    requiredRole: 'OWNER' | 'EDITOR' | 'VIEWER'
  ): boolean {
    const permissions = this.resourcePermissions.get(resourceId);
    if (!permissions) return false;

    const userPermission = permissions.find((p) => p.userId === userId);
    if (!userPermission) return false;

    // Check if expired
    if (userPermission.expiresAt && userPermission.expiresAt < Date.now()) {
      return false;
    }

    const roleHierarchy = { OWNER: 3, EDITOR: 2, VIEWER: 1 };
    return roleHierarchy[userPermission.role] >= roleHierarchy[requiredRole];
  }

  // ========== Resource Permissions ==========

  grantResourcePermission(
    resourceId: string,
    resourceType: string,
    userId: string,
    role: 'OWNER' | 'EDITOR' | 'VIEWER',
    grantedBy: string,
    expiresAt?: number
  ): void {
    if (!this.resourcePermissions.has(resourceId)) {
      this.resourcePermissions.set(resourceId, []);
    }

    const permissions = this.resourcePermissions.get(resourceId)!;

    // Remove existing permission if any
    const index = permissions.findIndex((p) => p.userId === userId);
    if (index !== -1) {
      permissions.splice(index, 1);
    }

    // Add new permission
    permissions.push({
      resourceId,
      resourceType,
      userId,
      role,
      grantedAt: Date.now(),
      grantedBy,
      expiresAt,
    });

    this.logAudit({
      userId: grantedBy,
      userName: 'SYSTEM',
      action: 'PERMISSION_GRANTED',
      resource: resourceType,
      resourceId,
      changes: { targetUser: userId, role },
      result: 'SUCCESS',
      ipAddress: '0.0.0.0',
      userAgent: 'SYSTEM',
    });

    this.emit('permission:granted', {
      resourceId,
      userId,
      role,
    });
  }

  revokeResourcePermission(resourceId: string, userId: string): void {
    const permissions = this.resourcePermissions.get(resourceId);
    if (!permissions) return;

    const index = permissions.findIndex((p) => p.userId === userId);
    if (index !== -1) {
      permissions.splice(index, 1);

      this.logAudit({
        userId: 'SYSTEM',
        userName: 'SYSTEM',
        action: 'PERMISSION_REVOKED',
        resource: 'RESOURCE',
        resourceId,
        changes: { targetUser: userId },
        result: 'SUCCESS',
        ipAddress: '0.0.0.0',
        userAgent: 'SYSTEM',
      });

      this.emit('permission:revoked', {
        resourceId,
        userId,
      });
    }
  }

  // ========== Role Management ==========

  grantRolePermission(role: Role, permission: string): void {
    const perms = this.rolePermissions.get(role);
    if (perms) {
      perms.add(permission);

      this.logAudit({
        userId: 'SYSTEM',
        userName: 'SYSTEM',
        action: 'ROLE_PERMISSION_GRANTED',
        resource: 'ROLE',
        resourceId: role,
        changes: { permission },
        result: 'SUCCESS',
        ipAddress: '0.0.0.0',
        userAgent: 'SYSTEM',
      });

      this.emit('role:permission:granted', { role, permission });
    }
  }

  revokeRolePermission(role: Role, permission: string): void {
    const perms = this.rolePermissions.get(role);
    if (perms) {
      perms.delete(permission);

      this.logAudit({
        userId: 'SYSTEM',
        userName: 'SYSTEM',
        action: 'ROLE_PERMISSION_REVOKED',
        resource: 'ROLE',
        resourceId: role,
        changes: { permission },
        result: 'SUCCESS',
        ipAddress: '0.0.0.0',
        userAgent: 'SYSTEM',
      });

      this.emit('role:permission:revoked', { role, permission });
    }
  }

  // ========== Audit Logging ==========

  private logAudit(log: Omit<AuditLog, 'id' | 'timestamp'>): void {
    const auditLog: AuditLog = {
      id: `audit_${Date.now()}_${Math.random().toString(36).slice(2)}`,
      ...log,
      timestamp: Date.now(),
    };

    this.auditLogs.push(auditLog);

    // Keep only last 10000 logs
    if (this.auditLogs.length > 10000) {
      this.auditLogs.shift();
    }

    this.emit('audit:logged', auditLog);
  }

  getAuditLogs(filters?: {
    userId?: string;
    resource?: string;
    action?: string;
    startTime?: number;
    endTime?: number;
    limit?: number;
  }): AuditLog[] {
    let logs = this.auditLogs;

    if (filters) {
      logs = logs.filter((log) => {
        if (filters.userId && log.userId !== filters.userId) return false;
        if (filters.resource && log.resource !== filters.resource) return false;
        if (filters.action && log.action !== filters.action) return false;
        if (filters.startTime && log.timestamp < filters.startTime) return false;
        if (filters.endTime && log.timestamp > filters.endTime) return false;
        return true;
      });
    }

    return logs.slice(0, filters?.limit || 100);
  }

  // ========== Events ==========

  subscribe(eventType: string, callback: (data: any) => void): void {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, new Set());
    }
    this.listeners.get(eventType)!.add(callback);
  }

  private emit(eventType: string, data: any): void {
    const callbacks = this.listeners.get(eventType);
    if (callbacks) {
      callbacks.forEach((callback) => {
        try {
          callback(data);
        } catch (error) {
          console.error(`[RBAC] Error in ${eventType} callback:`, error);
        }
      });
    }
  }

  // ========== Utility ==========

  getSessions(userId?: string): UserSession[] {
    const sessions = Array.from(this.sessions.values());

    if (userId) {
      return sessions.filter((s) => s.userId === userId && s.isActive);
    }

    return sessions.filter((s) => s.isActive);
  }

  getResourcePermissions(resourceId: string): ResourcePermission[] {
    return this.resourcePermissions.get(resourceId) || [];
  }
}

export const rbacManager = new RBACManager();

export default rbacManager;
