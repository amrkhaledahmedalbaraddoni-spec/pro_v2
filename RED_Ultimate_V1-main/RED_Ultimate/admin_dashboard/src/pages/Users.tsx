'use client';

import { useState, useCallback } from 'react';
import {
  ColumnDef,
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  SortingState,
  ColumnFiltersState,
  VisibilityState,
  PaginationState,
} from '@tanstack/react-table';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useVirtualizer } from '@tanstack/react-virtual';
import { apiFetch } from '../api';
import { Checkbox } from '@/components/ui/checkbox';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/utils/cn';
import { useTranslation } from 'react-i18next';
import { toast } from '@/hooks/useToast';
import { useRef } from 'react';
import { Dialog } from '@/components/ui/dialog';
import {
  Search,
  Filter,
  Download,
  MoreVertical,
  CheckCircle,
  XCircle,
  Shield,
  UserX,
  UserCheck,
  Mail,
  Phone,
  Calendar,
  Eye,
  Edit,
  Trash2,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Loader2,
  UserPlus,
  AlertTriangle,
  KeyRound,
  Sparkles,
  RefreshCw,
} from 'lucide-react';
import { format } from 'date-fns';
import { EmptyState } from './_shared';

interface User {
  id: string;
  username: string;
  displayName: string;
  email: string;
  phone: string;
  role: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'BANNED';
  avatarUrl?: string;
  createdAt: string;
  lastLogin: string;
  redId?: string;
  devices?: number;
  sessions?: number;
}

interface UsersResponse {
  content: User[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

const STATUS_COLORS: Record<User['status'], 'default' | 'success' | 'destructive' | 'warning' | 'info'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'destructive',
  SUSPENDED: 'warning',
  BANNED: 'destructive',
};

const STATUS_LABELS: Record<User['status'], string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
  BANNED: 'Banned',
};

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Super Admin',
  ADMIN: 'Admin',
  MODERATOR: 'Moderator',
  SUPPORT: 'Support',
  VIEWER: 'Viewer',
  USER: 'User',
};

// ✅ FIX 2026-09-22: columns كانت ثابت module-level يسترعي handle* (معرّفة داخل
// UsersPage) → "Cannot find name". الآن دالة تستقبل الـ handlers من المكوّن.
interface UserActions {
  t: (key: string) => string;
  handleView: (u: User) => void;
  handleEdit: (u: User) => void;
  handleApprove: (u: User) => void;
  handleReject: (u: User) => void;
  handleBan: (u: User) => void;
  handleUnban: (u: User) => void;
  handleImpersonate: (u: User) => void;
  handleDelete: (u: User) => void;
}

function buildColumns(a: UserActions): ColumnDef<User>[] {
  const { t, handleView, handleEdit, handleApprove, handleReject, handleBan, handleUnban, handleImpersonate, handleDelete } = a;
  return [
  {
    id: 'select',
    header: ({ table }) => (
      <Checkbox
        checked={table.getIsAllPageRowsSelected()}
        indeterminate={table.getIsSomePageRowsSelected() && !table.getIsAllPageRowsSelected()}
        onCheckedChange={(value) => table.toggleAllPageRowsSelected(!!value)}
        aria-label={t('users.bulkActions')}
      />
    ),
    cell: ({ row }) => (
      <Checkbox
        checked={row.getIsSelected()}
        onCheckedChange={(value) => row.toggleSelected(!!value)}
        aria-label={`${t('users.viewUser')}: ${row.original.username}`}
      />
    ),
    enableSorting: false,
    enableHiding: false,
    size: 40,
  },
  {
    accessorKey: 'username',
    header: 'Username',
    cell: ({ row }) => {
      const user = row.original;
      return (
        <div className="flex items-center gap-3">
          <Avatar className="h-8 w-8">
            <AvatarImage src={user.avatarUrl} alt={user.displayName} />
            <AvatarFallback className="text-xs">
              {user.displayName?.charAt(0).toUpperCase() || user.username.charAt(0).toUpperCase()}
            </AvatarFallback>
          </Avatar>
          <div>
            <p className="font-medium">{user.username}</p>
            <p className="text-xs text-muted-foreground">@{user.username}</p>
          </div>
        </div>
      );
    },
    size: 200,
  },
  {
    accessorKey: 'displayName',
    header: 'Display Name',
    cell: ({ row }) => row.original.displayName,
    size: 150,
  },
  {
    accessorKey: 'email',
    header: 'Email',
    cell: ({ row }) => (
      <a href={`mailto:${row.original.email}`} className="text-primary hover:underline">
        {row.original.email}
      </a>
    ),
    size: 200,
  },
  {
    accessorKey: 'phone',
    header: 'Phone',
    cell: ({ row }) => row.original.phone || '—',
    size: 130,
  },
  {
    accessorKey: 'role',
    header: 'Role',
    cell: ({ row }) => (
      <Badge variant="outline">{ROLE_LABELS[row.original.role] || row.original.role}</Badge>
    ),
    size: 120,
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }) => (
      <Badge variant={STATUS_COLORS[row.original.status]}>
        {STATUS_LABELS[row.original.status]}
      </Badge>
    ),
    size: 100,
  },
  {
    accessorKey: 'createdAt',
    header: 'Created',
    // ✅ 2026-09-24: format() يرمي RangeError على التواريخ التالفة → '—' بدل الانهيار
    cell: ({ row }) => {
      try {
        const d = new Date(row.original.createdAt);
        return Number.isFinite(d.getTime()) ? format(d, 'MMM d, yyyy') : '—';
      } catch {
        return '—';
      }
    },
    size: 120,
  },
  {
    accessorKey: 'lastLogin',
    header: 'Last Login',
    cell: ({ row }) => {
      if (!row.original.lastLogin) return t('common.never');
      try {
        const d = new Date(row.original.lastLogin);
        return Number.isFinite(d.getTime()) ? format(d, 'MMM d, yyyy HH:mm') : '—';
      } catch {
        return '—';
      }
    },
    size: 150,
  },
  {
    id: 'actions',
    header: 'Actions',
    cell: ({ row }) => (
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" className="h-8 w-8" aria-label={`Actions for ${row.original.username}`}>
            <MoreVertical className="h-4 w-4" aria-hidden="true" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuLabel>Actions for {row.original.username}</DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => handleView(row.original)}>
            <Eye className="h-4 w-4 mr-2" /> View Details
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => handleEdit(row.original)}>
            <Edit className="h-4 w-4 mr-2" /> Edit
          </DropdownMenuItem>
          {row.original.status === 'PENDING' && (
            <>
              <DropdownMenuItem onClick={() => handleApprove(row.original)} className="text-green-600">
                <CheckCircle className="h-4 w-4 mr-2" /> Approve
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleReject(row.original)} className="text-red-600">
                <XCircle className="h-4 w-4 mr-2" /> Reject
              </DropdownMenuItem>
            </>
          )}
          {row.original.status !== 'BANNED' && (
            <DropdownMenuItem onClick={() => handleBan(row.original)} className="text-orange-600">
              <UserX className="h-4 w-4 mr-2" /> Ban
            </DropdownMenuItem>
          )}
          {row.original.status === 'BANNED' && (
            <DropdownMenuItem onClick={() => handleUnban(row.original)} className="text-green-600">
              <UserCheck className="h-4 w-4 mr-2" /> Unban
            </DropdownMenuItem>
          )}
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => handleImpersonate(row.original)} className="text-blue-600">
            <Shield className="h-4 w-4 mr-2" /> Impersonate
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => handleDelete(row.original)} className="text-red-600">
            <Trash2 className="h-4 w-4 mr-2" /> Delete
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    ),
    enableSorting: false,
    size: 60,
  },
];
}

export function UsersPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [sorting, setSorting] = useState<SortingState>([{ id: 'createdAt', desc: true }]);
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
  const [globalFilter, setGlobalFilter] = useState('');
  const [pagination, setPagination] = useState<PaginationState>({ pageIndex: 0, pageSize: 20 });
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
  const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});
  const [selectedRows, setSelectedRows] = useState<Set<string>>(new Set());

  // 🚀 Create User Modal State
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [createForm, setCreateForm] = useState({
    username: '',
    displayName: '',
    email: '',
    phone: '',
    password: '',
    role: 'USER',
    status: 'APPROVED',
    redId: '',
    bio: '',
    avatarColor: '#00E59B',
  });

  // ⚠️ Delete User Modal State
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [userToDelete, setUserToDelete] = useState<User | null>(null);
  const [deleteIsHard, setDeleteIsHard] = useState(false);

  // Unified Dialog states — بديل prompt()/confirm() الأصلية (نفس السلوك)
  const [rejectDialog, setRejectDialog] = useState<{ open: boolean; user: User | null; reason: string }>({
    open: false, user: null, reason: '',
  });
  const [banDialog, setBanDialog] = useState<{ open: boolean; user: User | null; reason: string; duration: string }>({
    open: false, user: null, reason: '', duration: '',
  });
  const [unbanDialog, setUnbanDialog] = useState<{ open: boolean; user: User | null }>({ open: false, user: null });
  const [impersonateDialog, setImpersonateDialog] = useState<{ open: boolean; user: User | null }>({ open: false, user: null });
  const [bulkDialog, setBulkDialog] = useState<{ open: boolean; action: string; reason: string; duration: string }>({
    open: false, action: '', reason: '', duration: '',
  });

  const generateRandomPassword = () => {
    const chars = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#$%&*';
    let pwd = '';
    for (let i = 0; i < 14; i++) {
      pwd += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    setCreateForm(prev => ({ ...prev, password: pwd }));
    toast.info('تم توليد كلمة مرور قوية وتعيينها');
  };

  const generateRedIdCandidate = () => {
    const rand = Math.floor(10000 + Math.random() * 90000).toString();
    setCreateForm(prev => ({ ...prev, redId: rand }));
  };

  // Fetch users
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['users', pagination, sorting, columnFilters, globalFilter],
    queryFn: () => fetchUsers(pagination, sorting, columnFilters, globalFilter),
    placeholderData: (previousData) => previousData,
  });

  // Mutations
  const createMutation = useMutation({
    mutationFn: (form: typeof createForm) => createUser(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      setIsCreateModalOpen(false);
      setCreateForm({
        username: '',
        displayName: '',
        email: '',
        phone: '',
        password: '',
        role: 'USER',
        status: 'APPROVED',
        redId: '',
        bio: '',
        avatarColor: '#00E59B',
      });
      toast.success('تم إنشاء وتفعيل حساب المستخدم بنجاح');
    },
    onError: (err: Error) => toast.error(err.message),
  });

  const approveMutation = useMutation({
    mutationFn: (id: string) => approveUser(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); toast.success('User approved'); },
    onError: (err: Error) => toast.error(err.message),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => rejectUser(id, reason),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); toast.success('User rejected'); },
    onError: (err: Error) => toast.error(err.message),
  });

  const banMutation = useMutation({
    mutationFn: ({ id, reason, durationDays }: { id: string; reason: string; durationDays?: number }) => banUser(id, reason, durationDays),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); toast.success('User banned'); },
    onError: (err: Error) => toast.error(err.message),
  });

  const unbanMutation = useMutation({
    mutationFn: (id: string) => unbanUser(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); toast.success('User unbanned'); },
    onError: (err: Error) => toast.error(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: ({ id, hard }: { id: string; hard: boolean }) => deleteUser(id, hard),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      setIsDeleteModalOpen(false);
      setUserToDelete(null);
      setDeleteIsHard(false);
      if (variables.hard) {
        toast.success('تم مسح الحساب نهائياً وكافة بياناته وجلساته من النظام');
      } else {
        toast.success('تم حظر/تعطيل الحساب بنجاح');
      }
    },
    onError: (err: Error) => toast.error(err.message),
  });

  const bulkActionMutation = useMutation({
    mutationFn: ({ action, ids, reason, durationDays, hard }: { action: string; ids: string[]; reason?: string; durationDays?: number; hard?: boolean }) =>
      bulkAction(action, ids, reason, durationDays, hard),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); toast.success('Bulk action completed'); },
    onError: (err: Error) => toast.error(err.message),
  });

  // 👁️ View User Drawer State
  const [viewUser, setViewUser] = useState<User | null>(null);
  const [isViewOpen, setIsViewOpen] = useState(false);

  // ✏️ Edit User Dialog State
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [editUser, setEditUser] = useState<User | null>(null);
  const [editForm, setEditForm] = useState({
    displayName: '',
    email: '',
    phone: '',
    role: '',
    status: '',
    bio: '',
  });

  // Edit mutation
  const editMutation = useMutation({
    mutationFn: (data: { id: string; updates: typeof editForm }) =>
      updateUser(data.id, data.updates),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      setIsEditOpen(false);
      setEditUser(null);
      toast.success('تم تحديث بيانات المستخدم بنجاح');
    },
    onError: (err: Error) => toast.error(err.message),
  });

  const handleView = (user: User) => {
    setViewUser(user);
    setIsViewOpen(true);
  };

  const handleEdit = (user: User) => {
    setEditUser(user);
    setEditForm({
      displayName: user.displayName || '',
      email: user.email || '',
      phone: user.phone || '',
      role: user.role || 'USER',
      status: user.status || 'APPROVED',
      bio: '',
    });
    setIsEditOpen(true);
  };

  const handleApprove = (user: User) => {
    approveMutation.mutate(user.id);
  };

  const handleReject = (user: User) => {
    setRejectDialog({ open: true, user, reason: '' });
  };

  const handleBan = (user: User) => {
    setBanDialog({ open: true, user, reason: '', duration: '' });
  };

  const handleUnban = (user: User) => {
    setUnbanDialog({ open: true, user });
  };

  const handleImpersonate = (user: User) => {
    setImpersonateDialog({ open: true, user });
  };

  const handleDelete = (user: User) => {
    setUserToDelete(user);
    setDeleteIsHard(false);
    setIsDeleteModalOpen(true);
  };

  const handleBulkAction = (action: string) => {
    const selectedIds = Array.from(selectedRows);
    if (selectedIds.length === 0) return;

    if (action === 'reject' || action === 'ban' || action === 'delete') {
      setBulkDialog({ open: true, action, reason: '', duration: '' });
      return;
    }

    bulkActionMutation.mutate({ action, ids: selectedIds });
  };

  const confirmBulkDialog = () => {
    const selectedIds = Array.from(selectedRows);
    if (selectedIds.length === 0) return;
    const { action, reason, duration } = bulkDialog;
    if ((action === 'reject' || action === 'ban') && !reason) return;
    const durationDays = duration ? parseInt(duration) : undefined;
    bulkActionMutation.mutate({
      action,
      ids: selectedIds,
      reason: reason || undefined,
      durationDays,
    });
    setBulkDialog({ open: false, action: '', reason: '', duration: '' });
  };

  const columns = buildColumns({ t, handleView, handleEdit, handleApprove, handleReject, handleBan, handleUnban, handleImpersonate, handleDelete });

  const table = useReactTable({
    data: data?.content || [],
    columns,
    state: {
      sorting,
      columnFilters,
      globalFilter,
      pagination,
      columnVisibility,
      rowSelection,
    },
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    onGlobalFilterChange: setGlobalFilter,
    onPaginationChange: setPagination,
    onColumnVisibilityChange: setColumnVisibility,
    onRowSelectionChange: setRowSelection,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    manualPagination: true,
    pageCount: data?.totalPages || 1,
    initialState: {
      pagination: { pageIndex: 0, pageSize: 20 },
    },
  });

  // Virtualizer for large datasets
  const parentRef = useRef<HTMLTableSectionElement>(null);
  const virtualizer = useVirtualizer({
    count: table.getRowModel().rows.length,
    getScrollElement: () => parentRef.current?.parentElement ?? null,
    estimateSize: () => 56,
    overscan: 5,
  });

  const handleExport = async (format: 'csv' | 'excel' | 'pdf') => {
    toast.info(`Exporting as ${format.toUpperCase()}...`);
  };

  return (
    <>
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('users.title')}</h1>
          {/* ✅ 2026-09-24: بدل الصفر الصامت — '—' أثناء التحميل/الخطأ */}
          <p className="text-foreground/70">{typeof data?.totalElements === 'number' ? `${data.totalElements} ${t('users.totalUsers')}` : `— ${t('users.totalUsers')}`}</p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <Button
            onClick={() => {
              generateRedIdCandidate();
              setIsCreateModalOpen(true);
            }}
            className="bg-emerald-500 hover:bg-emerald-600 text-black font-semibold shadow-lg shadow-emerald-500/20"
          >
            <UserPlus className="h-4 w-4 mr-2" /> إضافة مستخدم جديد
          </Button>
          <Button variant="outline" onClick={() => handleExport('csv')} aria-label={`${t('common.export')} CSV`}>
            <Download className="h-4 w-4 mr-2" aria-hidden="true" /> CSV
          </Button>
          <Button variant="outline" onClick={() => handleExport('excel')} aria-label={`${t('common.export')} Excel`}>
            <Download className="h-4 w-4 mr-2" aria-hidden="true" /> Excel
          </Button>
          <Button variant="outline" onClick={() => handleExport('pdf')} aria-label={`${t('common.export')} PDF`}>
            <Download className="h-4 w-4 mr-2" aria-hidden="true" /> PDF
          </Button>
        </div>
      </div>

      {/* Toolbar */}
      <Card>
        <CardContent className="p-4">
          <div className="flex flex-col sm:flex-row gap-4">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder={t('users.searchPlaceholder')}
                value={globalFilter}
                onChange={(e) => setGlobalFilter(e.target.value)}
                className="pl-10"
              />
            </div>
            <Select value={columnFilters.find(f => f.id === 'status')?.value as string || ''} onValueChange={(v) => setColumnFilters(prev => [...prev.filter(f => f.id !== 'status'), { id: 'status', value: v }])}>
              <SelectTrigger className="w-[150px]">
                <SelectValue placeholder={t('users.filters.status')} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">{t('common.all')}</SelectItem>
                <SelectItem value="PENDING">Pending</SelectItem>
                <SelectItem value="APPROVED">Approved</SelectItem>
                <SelectItem value="REJECTED">Rejected</SelectItem>
                <SelectItem value="SUSPENDED">Suspended</SelectItem>
                <SelectItem value="BANNED">Banned</SelectItem>
              </SelectContent>
            </Select>
            <Select value={columnFilters.find(f => f.id === 'role')?.value as string || ''} onValueChange={(v) => setColumnFilters(prev => [...prev.filter(f => f.id !== 'role'), { id: 'role', value: v }])}>
              <SelectTrigger className="w-[150px]">
                <SelectValue placeholder={t('users.filters.role')} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">{t('common.all')}</SelectItem>
                <SelectItem value="SUPER_ADMIN">Super Admin</SelectItem>
                <SelectItem value="ADMIN">Admin</SelectItem>
                <SelectItem value="MODERATOR">Moderator</SelectItem>
                <SelectItem value="SUPPORT">Support</SelectItem>
                <SelectItem value="VIEWER">Viewer</SelectItem>
              </SelectContent>
            </Select>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline">
                  <Filter className="h-4 w-4 mr-2" /> Columns
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {table.getAllColumns().map((column) => (
                  <DropdownMenuItem key={column.id} className="flex items-center gap-2">
                    <Checkbox
                      checked={column.getIsVisible()}
                      onCheckedChange={(value) => column.toggleVisibility(!!value)}
                    />
                    {column.id}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </CardContent>
      </Card>

      {/* Bulk Actions Bar */}
      {selectedRows.size > 0 && (
        <Card className="border-yellow-500 bg-yellow-50">
          <CardContent className="p-4">
            <div className="flex items-center justify-between">
              <span className="font-medium">{t('users.selected', { count: selectedRows.size })}</span>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={() => handleBulkAction('approve')}>
                  <CheckCircle className="h-4 w-4 mr-2" /> Approve
                </Button>
                <Button variant="outline" size="sm" onClick={() => handleBulkAction('reject')}>
                  <XCircle className="h-4 w-4 mr-2" /> Reject
                </Button>
                <Button variant="outline" size="sm" onClick={() => handleBulkAction('ban')}>
                  <UserX className="h-4 w-4 mr-2" /> Ban
                </Button>
                <Button variant="outline" size="sm" onClick={() => handleBulkAction('unban')}>
                  <UserCheck className="h-4 w-4 mr-2" /> Unban
                </Button>
                <Button variant="destructive" size="sm" onClick={() => handleBulkAction('delete')}>
                  <Trash2 className="h-4 w-4 mr-2" /> Delete
                </Button>
                <Button variant="ghost" size="sm" onClick={() => { setSelectedRows(new Set()); table.resetRowSelection(); }}>
                  Clear
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="p-6">
              <div className="space-y-4">
                {[1, 2, 3, 4, 5].map((i) => (
                  <div key={i} className="flex items-center gap-4 p-4">
                    <Skeleton className="h-8 w-8 rounded-full" />
                    <div className="flex-1 space-y-2">
                      <Skeleton className="h-4 w-32" />
                      <Skeleton className="h-3 w-48" />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : error ? (
            <div className="p-6 text-center text-red-700 dark:text-red-400" role="alert">
              {t('common.loadError')}: {error.message}
              <Button variant="outline" className="ml-4" onClick={() => refetch()}>{t('common.retry')}</Button>
            </div>
          ) : (data?.content?.length ?? 0) === 0 ? (
            <div className="p-6">
              <EmptyState />
            </div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full" role="grid">
                  <thead className="bg-muted/50">
                    {table.getHeaderGroups().map((headerGroup) => (
                      <tr key={headerGroup.id}>
                        {headerGroup.headers.map((header) => (
                          <th
                            key={header.id}
                            className={cn(
                              'h-12 px-4 text-left text-xs font-semibold text-foreground/70 uppercase tracking-wider',
                              header.column.getCanSort() && 'cursor-pointer select-none hover:bg-muted',
                              !header.column.getCanSort() && 'cursor-default'
                            )}
                            onClick={header.column.getToggleSortingHandler()}
                            style={{
                              width: header.column.getSize(),
                              minWidth: header.column.getSize(),
                            }}
                          >
                            <div className="flex items-center gap-2">
                              {flexRender(header.column.columnDef.header, header.getContext())}
                              {header.column.getIsSorted() && (
                                <ArrowUpDown className="h-4 w-4" />
                              )}
                            </div>
                          </th>
                        ))}
                      </tr>
                    ))}
                  </thead>
                  <tbody
                    ref={parentRef}
                    className="h-[calc(100vh-400px)] max-h-[600px] overflow-y-auto"
                  >
                    <div style={{ height: virtualizer.getTotalSize() }}>
                      {virtualizer.getVirtualItems().map((virtualRow) => (
                        <tr
                          key={virtualRow.key}
                          style={{
                            position: 'absolute',
                            top: 0,
                            left: 0,
                            width: '100%',
                            height: `${virtualRow.size}px`,
                            transform: `translateY(${virtualRow.start}px)`,
                          }}
                          className={cn(
                            'border-b transition-colors',
                            virtualRow.index % 2 === 0 ? 'bg-background' : 'bg-muted/30',
                            'hover:bg-accent/50'
                          )}
                        >
                          {table.getRowModel().rows[virtualRow.index]?.getVisibleCells().map((cell) => (
                            <td
                              key={cell.id}
                              className="px-4 py-3 align-middle"
                              style={{ width: cell.column.getSize() }}
                            >
                              {flexRender(cell.column.columnDef.cell, cell.getContext())}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </div>
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              <div className="flex items-center justify-between p-4 border-t">
                <div className="text-sm text-foreground/70">
                  Page {table.getState().pagination.pageIndex + 1} of {table.getPageCount()}
                  {' '}({typeof data?.totalElements === 'number' ? data.totalElements : '—'} total)
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => table.previousPage()}
                    disabled={!table.getCanPreviousPage()}
                    aria-label="First page"
                  >
                    <ChevronsLeft className="h-4 w-4" aria-hidden="true" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => table.previousPage()}
                    disabled={!table.getCanPreviousPage()}
                    aria-label="Previous page"
                  >
                    <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => table.nextPage()}
                    disabled={!table.getCanNextPage()}
                    aria-label="Next page"
                  >
                    <ChevronRight className="h-4 w-4" aria-hidden="true" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => table.nextPage()}
                    disabled={!table.getCanNextPage()}
                    aria-label="Last page"
                  >
                    <ChevronsRight className="h-4 w-4" aria-hidden="true" />
                  </Button>
                  <Select
                    value={String(table.getState().pagination.pageSize)}
                    onValueChange={(v) => table.setPageSize(Number(v))}
                  >
                    <SelectTrigger className="w-[100px]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {[10, 20, 50, 100].map((size) => (
                        <SelectItem key={size} value={String(size)}>
                          {size} per page
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      {/* 🚀 Create User Dialog */}
      <Dialog
        open={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        title="✨ إضافة وتفعيل مستخدم سيادي جديد (New Sovereign User)"
        size="lg"
      >
        <div className="space-y-4 py-2 text-right" dir="rtl">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-yn-text-secondary">اسم المستخدم (Username) *</label>
              <Input
                placeholder="younes_dev"
                value={createForm.username}
                onChange={(e) => setCreateForm({ ...createForm, username: e.target.value })}
                className="font-mono text-left"
                dir="ltr"
              />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-semibold text-yn-text-secondary">الاسم الظاهر (Display Name)</label>
              <Input
                placeholder="يونس الحربي"
                value={createForm.displayName}
                onChange={(e) => setCreateForm({ ...createForm, displayName: e.target.value })}
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-yn-text-secondary">البريد الإلكتروني (اختياري)</label>
              <Input
                type="email"
                placeholder="user@red.sovereign"
                value={createForm.email}
                onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })}
                className="font-mono text-left"
                dir="ltr"
              />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-semibold text-yn-text-secondary">رقم الهاتف (اختياري)</label>
              <Input
                placeholder="+967770000000"
                value={createForm.phone}
                onChange={(e) => setCreateForm({ ...createForm, phone: e.target.value })}
                className="font-mono text-left"
                dir="ltr"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-1">
              <div className="flex items-center justify-between">
                <label className="text-xs font-semibold text-yn-text-secondary">كلمة المرور (Argon2id) *</label>
                <button
                  type="button"
                  onClick={generateRandomPassword}
                  className="text-xs text-primary hover:underline flex items-center gap-1"
                >
                  <Sparkles className="w-3 h-3" /> توليد عشوائي
                </button>
              </div>
              <Input
                type="text"
                placeholder="••••••••••••"
                value={createForm.password}
                onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })}
                className="font-mono text-left"
                dir="ltr"
              />
            </div>
            <div className="space-y-1">
              <div className="flex items-center justify-between">
                <label className="text-xs font-semibold text-yn-text-secondary">معرّف RED ID السيادي (5 أرقام)</label>
                <button
                  type="button"
                  onClick={generateRedIdCandidate}
                  className="text-xs text-primary hover:underline flex items-center gap-1"
                >
                  <RefreshCw className="w-3 h-3" /> توليد جديد
                </button>
              </div>
              <Input
                type="text"
                placeholder="مثال: 58492"
                value={createForm.redId}
                onChange={(e) => setCreateForm({ ...createForm, redId: e.target.value })}
                className="font-mono text-center tracking-widest font-bold text-primary"
                dir="ltr"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-yn-text-secondary">الصلاحية والرتبة (Role)</label>
              <Select
                value={createForm.role}
                onValueChange={(val) => setCreateForm({ ...createForm, role: val })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="USER">مستخدم عادي (User)</SelectItem>
                  <SelectItem value="ADMIN">مدير نظام (Admin)</SelectItem>
                  <SelectItem value="SUPER_ADMIN">مدير سيادي أعلى (Super Admin)</SelectItem>
                  <SelectItem value="MODERATOR">مشرف محتوى (Moderator)</SelectItem>
                  <SelectItem value="SUPPORT">دعم فني (Support)</SelectItem>
                  <SelectItem value="VIEWER">مراقب فقط (Viewer)</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              <label className="text-xs font-semibold text-yn-text-secondary">الحالة الأولية للحساب (Status)</label>
              <Select
                value={createForm.status}
                onValueChange={(val) => setCreateForm({ ...createForm, status: val })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="APPROVED">مفعل فوري (APPROVED)</SelectItem>
                  <SelectItem value="PENDING">معلق للمراجعة (PENDING)</SelectItem>
                  <SelectItem value="SUSPENDED">معطل مؤقتاً (SUSPENDED)</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="space-y-1">
            <label className="text-xs font-semibold text-yn-text-secondary">نبذة تعريفية (Bio)</label>
            <Input
              placeholder="وصف أو ملاحظات الإدارة حول الحساب..."
              value={createForm.bio}
              onChange={(e) => setCreateForm({ ...createForm, bio: e.target.value })}
            />
          </div>

          <div className="space-y-2">
            <label className="text-xs font-semibold text-yn-text-secondary">لون الأفاتار السيادي المميز</label>
            <div className="flex items-center gap-3">
              {['#00E59B', '#3399FF', '#FFC000', '#A855F7', '#EC4899', '#EF4444'].map((color) => (
                <button
                  key={color}
                  type="button"
                  onClick={() => setCreateForm({ ...createForm, avatarColor: color })}
                  className={cn(
                    'w-7 h-7 rounded-full border-2 transition-all',
                    createForm.avatarColor === color ? 'border-white scale-110 shadow-lg' : 'border-transparent opacity-70 hover:opacity-100'
                  )}
                  style={{ backgroundColor: color }}
                  aria-label={`Select color ${color}`}
                />
              ))}
              <span className="text-xs font-mono text-muted-foreground mr-2">{createForm.avatarColor}</span>
            </div>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-yn-border">
            <Button
              variant="outline"
              onClick={() => setIsCreateModalOpen(false)}
            >
              إلغاء
            </Button>
            <Button
              disabled={createMutation.isPending || !createForm.username || !createForm.password}
              onClick={() => createMutation.mutate(createForm)}
              className="bg-emerald-500 hover:bg-emerald-600 text-black font-semibold"
            >
              {createMutation.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 ml-2 animate-spin" />
                  جاري إنشاء الحساب...
                </>
              ) : (
                <>
                  <CheckCircle className="w-4 h-4 ml-2" />
                  إنشاء وتفعيل الحساب الآن
                </>
              )}
            </Button>
          </div>
        </div>
      </Dialog>

      {/* ⚠️ Delete / Hard Wipe User Dialog */}
      <Dialog
        open={isDeleteModalOpen}
        onClose={() => {
          setIsDeleteModalOpen(false);
          setUserToDelete(null);
          setDeleteIsHard(false);
        }}
        title={deleteIsHard ? '⚠️ تأكيد مسح المستخدم نهائياً (Permanent Hard Wipe)' : 'تعطيل / حظر المستخدم'}
        size="md"
      >
        {userToDelete && (
          <div className="space-y-4 py-2 text-right" dir="rtl">
            <div className="flex items-center gap-3 p-3 bg-yn-surface rounded-lg border border-yn-border">
              <Avatar className="h-10 w-10 border border-primary/30">
                <AvatarImage src={userToDelete.avatarUrl} alt={userToDelete.displayName} />
                <AvatarFallback className="text-sm font-bold">
                  {userToDelete.displayName?.charAt(0).toUpperCase() || userToDelete.username.charAt(0).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <div>
                <p className="font-bold text-yn-text">{userToDelete.displayName || userToDelete.username}</p>
                <p className="text-xs font-mono text-yn-text-secondary">@{userToDelete.username} • RED ID: #{userToDelete.redId || '—'}</p>
              </div>
            </div>

            {deleteIsHard ? (
              <div className="p-4 rounded-lg bg-red-950/40 border border-red-500/40 text-red-200 space-y-2">
                <div className="flex items-center gap-2 font-bold text-red-400">
                  <AlertTriangle className="w-5 h-5 text-red-400 shrink-0" />
                  <span>تحذير أمني حرج: مسح جذري كامل ولا يمكن التراجع!</span>
                </div>
                <p className="text-xs leading-relaxed text-red-300">
                  سيتم حذف هذا الحساب بصورة دائمة وفورية من كافة قواعد البيانات:
                </p>
                <ul className="text-xs list-disc list-inside space-y-1 text-red-300/90 font-mono">
                  <li>PostgreSQL: إزالة سجل الحساب، الأجهزة، المفاتيح، والملفات الشخصية.</li>
                  <li>MongoDB: محو كافة رسائل الدردشات، والمحادثات المشفرة، وسجل المكالمات.</li>
                  <li>Redis: إنهاء وحرق كافة الجلسات النشطة والتوكنز ومفاتيح الكاش فوراً.</li>
                </ul>
              </div>
            ) : (
              <div className="p-4 rounded-lg bg-amber-950/40 border border-amber-500/40 text-amber-200 space-y-2">
                <div className="flex items-center gap-2 font-bold text-amber-400">
                  <Shield className="w-5 h-5 text-amber-400 shrink-0" />
                  <span>تعطيل وحظر الحساب (Soft Ban)</span>
                </div>
                <p className="text-xs leading-relaxed text-amber-300">
                  سيتم تغيير حالة الحساب إلى محظور (BANNED) وإنهاء جلساته، مع الاحتفاظ ببياناته ورسائله لضمان الأرشفة واسترجاعها لاحقاً إذا لزم الأمر.
                </p>
              </div>
            )}

            <div className="p-3 bg-yn-surface/60 rounded-lg border border-yn-border flex items-center justify-between">
              <label htmlFor="hard-wipe-toggle" className="text-xs font-semibold cursor-pointer select-none">
                {deleteIsHard ? (
                  <span className="text-red-400 font-bold">تفعيل المسح الجذري الشامل (Permanent Hard Wipe)</span>
                ) : (
                  <span className="text-yn-text-secondary">هل تريد المسح النهائي الشامل بدلاً من الحظر؟</span>
                )}
              </label>
              <Checkbox
                id="hard-wipe-toggle"
                checked={deleteIsHard}
                onCheckedChange={(val) => setDeleteIsHard(!!val)}
              />
            </div>

            <div className="flex items-center justify-end gap-3 pt-4 border-t border-yn-border">
              <Button
                variant="outline"
                onClick={() => {
                  setIsDeleteModalOpen(false);
                  setUserToDelete(null);
                  setDeleteIsHard(false);
                }}
              >
                إلغاء
              </Button>
              <Button
                variant={deleteIsHard ? 'destructive' : 'default'}
                disabled={deleteMutation.isPending}
                onClick={() => {
                  if (userToDelete) {
                    deleteMutation.mutate({ id: userToDelete.id, hard: deleteIsHard });
                  }
                }}
                className={deleteIsHard ? 'bg-red-600 hover:bg-red-700 text-white font-bold' : ''}
              >
                {deleteMutation.isPending ? (
                  <>
                    <Loader2 className="w-4 h-4 ml-2 animate-spin" />
                    جاري التنفيذ...
                  </>
                ) : deleteIsHard ? (
                  <>
                    <Trash2 className="w-4 h-4 ml-2" />
                    تأكيد المسح الجذري النهائي
                  </>
                ) : (
                  <>
                    <UserX className="w-4 h-4 ml-2" />
                    تأكيد حظر الحساب
                  </>
                )}
              </Button>
            </div>
          </div>
        )}
      </Dialog>

      {/* 👁️ View User Detail Dialog */}
      <Dialog
        open={isViewOpen}
        onClose={() => { setIsViewOpen(false); setViewUser(null); }}
        title={viewUser ? `👤 ${viewUser.displayName || viewUser.username}` : 'تفاصيل المستخدم'}
        size="lg"
      >
        {viewUser && (
          <div className="space-y-5 py-2" dir="rtl">
            {/* User Header */}
            <div className="flex items-center gap-4 p-4 rounded-xl bg-gradient-to-l from-emerald-950/30 to-transparent border border-emerald-500/20">
              <Avatar className="h-16 w-16 border-2 border-primary/40 shadow-lg shadow-emerald-500/10">
                <AvatarImage src={viewUser.avatarUrl} alt={viewUser.displayName} />
                <AvatarFallback className="text-xl font-bold bg-primary/20 text-primary">
                  {viewUser.displayName?.charAt(0).toUpperCase() || viewUser.username.charAt(0).toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <div className="flex-1">
                <h3 className="text-lg font-bold">{viewUser.displayName || viewUser.username}</h3>
                <p className="text-sm font-mono text-muted-foreground">@{viewUser.username}</p>
                {viewUser.redId && (
                  <span className="inline-block mt-1 px-2 py-0.5 text-xs font-mono font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded">
                    RED #{viewUser.redId}
                  </span>
                )}
              </div>
              <div className="flex flex-col items-end gap-2">
                <Badge variant={STATUS_COLORS[viewUser.status]}>{STATUS_LABELS[viewUser.status]}</Badge>
                <Badge variant="default">{ROLE_LABELS[viewUser.role] || viewUser.role}</Badge>
              </div>
            </div>

            {/* Info Grid */}
            <div className="grid grid-cols-2 gap-3">
              <div className="p-3 rounded-lg bg-card border border-border">
                <p className="text-xs text-muted-foreground mb-1">📧 البريد الإلكتروني</p>
                <p className="text-sm font-mono">{viewUser.email || '—'}</p>
              </div>
              <div className="p-3 rounded-lg bg-card border border-border">
                <p className="text-xs text-muted-foreground mb-1">📱 رقم الهاتف</p>
                <p className="text-sm font-mono" dir="ltr">{viewUser.phone || '—'}</p>
              </div>
              <div className="p-3 rounded-lg bg-card border border-border">
                <p className="text-xs text-muted-foreground mb-1">📅 تاريخ الإنشاء</p>
                <p className="text-sm">{viewUser.createdAt ? format(new Date(viewUser.createdAt), 'yyyy/MM/dd HH:mm') : '—'}</p>
              </div>
              <div className="p-3 rounded-lg bg-card border border-border">
                <p className="text-xs text-muted-foreground mb-1">🕐 آخر تسجيل دخول</p>
                <p className="text-sm">{viewUser.lastLogin ? format(new Date(viewUser.lastLogin), 'yyyy/MM/dd HH:mm') : '—'}</p>
              </div>
              <div className="p-3 rounded-lg bg-card border border-border">
                <p className="text-xs text-muted-foreground mb-1">📲 الأجهزة المسجلة</p>
                <p className="text-sm font-bold">{viewUser.devices ?? '—'}</p>
              </div>
              <div className="p-3 rounded-lg bg-card border border-border">
                <p className="text-xs text-muted-foreground mb-1">🔑 الجلسات النشطة</p>
                <p className="text-sm font-bold">{viewUser.sessions ?? '—'}</p>
              </div>
            </div>

            {/* Quick Actions */}
            <div className="flex items-center gap-2 pt-3 border-t border-border flex-wrap">
              <Button
                variant="outline"
                size="sm"
                onClick={() => { setIsViewOpen(false); handleEdit(viewUser); }}
              >
                <Edit className="w-4 h-4 ml-1" /> تعديل البيانات
              </Button>
              {viewUser.status === 'PENDING' && (
                <Button
                  size="sm"
                  onClick={() => { setIsViewOpen(false); handleApprove(viewUser); }}
                  className="bg-emerald-500 hover:bg-emerald-600 text-black"
                >
                  <CheckCircle className="w-4 h-4 ml-1" /> قبول الحساب
                </Button>
              )}
              {viewUser.status === 'APPROVED' && (
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => { setIsViewOpen(false); handleBan(viewUser); }}
                >
                  <UserX className="w-4 h-4 ml-1" /> حظر
                </Button>
              )}
              {viewUser.status === 'BANNED' && (
                <Button
                  size="sm"
                  onClick={() => { setIsViewOpen(false); handleUnban(viewUser); }}
                >
                  <UserCheck className="w-4 h-4 ml-1" /> إلغاء الحظر
                </Button>
              )}
              <Button
                variant="destructive"
                size="sm"
                onClick={() => { setIsViewOpen(false); handleDelete(viewUser); }}
              >
                <Trash2 className="w-4 h-4 ml-1" /> حذف
              </Button>
            </div>
          </div>
        )}
      </Dialog>

      {/* ✏️ Edit User Dialog */}
      <Dialog
        open={isEditOpen}
        onClose={() => { setIsEditOpen(false); setEditUser(null); }}
        title={editUser ? `✏️ تعديل: ${editUser.displayName || editUser.username}` : 'تعديل المستخدم'}
        size="lg"
      >
        {editUser && (
          <div className="space-y-4 py-2" dir="rtl">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-1">
                <label className="text-xs font-semibold text-muted-foreground">الاسم الظاهر (Display Name)</label>
                <Input
                  value={editForm.displayName}
                  onChange={(e) => setEditForm({ ...editForm, displayName: e.target.value })}
                  placeholder="يونس الحربي"
                />
              </div>
              <div className="space-y-1">
                <label className="text-xs font-semibold text-muted-foreground">اسم المستخدم (للقراءة فقط)</label>
                <Input value={editUser.username} disabled className="opacity-60 font-mono" dir="ltr" />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-1">
                <label className="text-xs font-semibold text-muted-foreground">البريد الإلكتروني</label>
                <Input
                  type="email"
                  value={editForm.email}
                  onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
                  placeholder="user@red.sovereign"
                  className="font-mono" dir="ltr"
                />
              </div>
              <div className="space-y-1">
                <label className="text-xs font-semibold text-muted-foreground">رقم الهاتف</label>
                <Input
                  value={editForm.phone}
                  onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
                  placeholder="+967770000000"
                  className="font-mono" dir="ltr"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-1">
                <label className="text-xs font-semibold text-muted-foreground">الصلاحية (Role)</label>
                <Select value={editForm.role} onValueChange={(val) => setEditForm({ ...editForm, role: val })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="USER">مستخدم عادي</SelectItem>
                    <SelectItem value="ADMIN">مدير نظام</SelectItem>
                    <SelectItem value="SUPER_ADMIN">مدير سيادي أعلى</SelectItem>
                    <SelectItem value="MODERATOR">مشرف محتوى</SelectItem>
                    <SelectItem value="SUPPORT">دعم فني</SelectItem>
                    <SelectItem value="VIEWER">مراقب فقط</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <label className="text-xs font-semibold text-muted-foreground">الحالة (Status)</label>
                <Select value={editForm.status} onValueChange={(val) => setEditForm({ ...editForm, status: val })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="APPROVED">مفعل</SelectItem>
                    <SelectItem value="PENDING">معلق</SelectItem>
                    <SelectItem value="SUSPENDED">معطل</SelectItem>
                    <SelectItem value="BANNED">محظور</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="flex items-center justify-end gap-3 pt-4 border-t border-border">
              <Button variant="outline" onClick={() => { setIsEditOpen(false); setEditUser(null); }}>
                إلغاء
              </Button>
              <Button
                disabled={editMutation.isPending}
                onClick={() => editUser && editMutation.mutate({ id: editUser.id, updates: editForm })}
                className="bg-emerald-500 hover:bg-emerald-600 text-black font-semibold"
              >
                {editMutation.isPending ? (
                  <><Loader2 className="w-4 h-4 ml-2 animate-spin" /> جاري الحفظ...</>
                ) : (
                  <><CheckCircle className="w-4 h-4 ml-2" /> حفظ التعديلات</>
                )}
              </Button>
            </div>
          </div>
        )}
      </Dialog>

      {/* Reject Dialog — بديل prompt السبب */}
      <Dialog
        open={rejectDialog.open}
        onOpenChange={(open) => { if (!open) setRejectDialog({ open: false, user: null, reason: '' }); }}
        title={rejectDialog.user ? `Reject ${rejectDialog.user.username}` : 'Reject user'}
      >
        <div className="space-y-4 py-2">
          <div className="space-y-1">
            <label className="text-xs font-semibold text-muted-foreground">Reason for rejection *</label>
            <Input
              value={rejectDialog.reason}
              onChange={(e) => setRejectDialog({ ...rejectDialog, reason: e.target.value })}
              placeholder="Reason for rejection"
            />
          </div>
          <div className="flex items-center justify-end gap-2">
            <Button variant="outline" onClick={() => setRejectDialog({ open: false, user: null, reason: '' })}>Cancel</Button>
            <Button
              variant="destructive"
              disabled={!rejectDialog.reason || !rejectDialog.user}
              onClick={() => {
                if (rejectDialog.user && rejectDialog.reason) {
                  rejectMutation.mutate({ id: rejectDialog.user.id, reason: rejectDialog.reason });
                  setRejectDialog({ open: false, user: null, reason: '' });
                }
              }}
            >
              Confirm reject
            </Button>
          </div>
        </div>
      </Dialog>

      {/* Ban Dialog — بديل prompt السبب/المدة */}
      <Dialog
        open={banDialog.open}
        onOpenChange={(open) => { if (!open) setBanDialog({ open: false, user: null, reason: '', duration: '' }); }}
        title={banDialog.user ? `Ban ${banDialog.user.username}` : 'Ban user'}
      >
        <div className="space-y-4 py-2">
          <div className="space-y-1">
            <label className="text-xs font-semibold text-muted-foreground">Reason for ban *</label>
            <Input
              value={banDialog.reason}
              onChange={(e) => setBanDialog({ ...banDialog, reason: e.target.value })}
              placeholder="Reason for ban"
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-semibold text-muted-foreground">Duration in days (optional)</label>
            <Input
              value={banDialog.duration}
              onChange={(e) => setBanDialog({ ...banDialog, duration: e.target.value })}
              placeholder="Duration in days (optional)"
            />
          </div>
          <div className="flex items-center justify-end gap-2">
            <Button variant="outline" onClick={() => setBanDialog({ open: false, user: null, reason: '', duration: '' })}>Cancel</Button>
            <Button
              variant="destructive"
              disabled={!banDialog.reason || !banDialog.user}
              onClick={() => {
                if (banDialog.user && banDialog.reason) {
                  banMutation.mutate({
                    id: banDialog.user.id,
                    reason: banDialog.reason,
                    durationDays: banDialog.duration ? parseInt(banDialog.duration) : undefined,
                  });
                  setBanDialog({ open: false, user: null, reason: '', duration: '' });
                }
              }}
            >
              Confirm ban
            </Button>
          </div>
        </div>
      </Dialog>

      {/* Unban Dialog — بديل confirm */}
      <Dialog
        open={unbanDialog.open}
        onOpenChange={(open) => { if (!open) setUnbanDialog({ open: false, user: null }); }}
        title={unbanDialog.user ? `Unban ${unbanDialog.user.username}?` : 'Unban user'}
      >
        <div className="flex items-center justify-end gap-2 py-2">
          <Button variant="outline" onClick={() => setUnbanDialog({ open: false, user: null })}>Cancel</Button>
          <Button
            disabled={!unbanDialog.user}
            onClick={() => {
              if (unbanDialog.user) unbanMutation.mutate(unbanDialog.user.id);
              setUnbanDialog({ open: false, user: null });
            }}
          >
            Confirm unban
          </Button>
        </div>
      </Dialog>

      {/* Impersonate Dialog — بديل confirm */}
      <Dialog
        open={impersonateDialog.open}
        onOpenChange={(open) => { if (!open) setImpersonateDialog({ open: false, user: null }); }}
        title={impersonateDialog.user ? `Impersonate ${impersonateDialog.user.username}? This will be logged.` : 'Impersonate user'}
      >
        <div className="flex items-center justify-end gap-2 py-2">
          <Button variant="outline" onClick={() => setImpersonateDialog({ open: false, user: null })}>Cancel</Button>
          <Button
            onClick={() => {
              toast.info('Impersonation started', 'Activity is being logged');
              setImpersonateDialog({ open: false, user: null });
            }}
          >
            Confirm
          </Button>
        </div>
      </Dialog>

      {/* Bulk Dialog — بديل prompt/confirm الجماعية */}
      <Dialog
        open={bulkDialog.open}
        onOpenChange={(open) => { if (!open) setBulkDialog({ open: false, action: '', reason: '', duration: '' }); }}
        title={
          bulkDialog.action === 'reject' ? `Reject ${selectedRows.size} users`
          : bulkDialog.action === 'ban' ? `Ban ${selectedRows.size} users`
          : `Delete ${selectedRows.size} users? This cannot be undone.`
        }
      >
        <div className="space-y-4 py-2">
          {(bulkDialog.action === 'reject' || bulkDialog.action === 'ban') && (
            <>
              <div className="space-y-1">
                <label className="text-xs font-semibold text-muted-foreground">
                  {bulkDialog.action === 'reject' ? 'Reason for rejection *' : 'Reason for ban *'}
                </label>
                <Input
                  value={bulkDialog.reason}
                  onChange={(e) => setBulkDialog({ ...bulkDialog, reason: e.target.value })}
                  placeholder={bulkDialog.action === 'reject' ? 'Reason for rejection' : 'Reason for ban'}
                />
              </div>
              {bulkDialog.action === 'ban' && (
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-muted-foreground">Duration in days (optional)</label>
                  <Input
                    value={bulkDialog.duration}
                    onChange={(e) => setBulkDialog({ ...bulkDialog, duration: e.target.value })}
                    placeholder="Duration in days (optional)"
                  />
                </div>
              )}
            </>
          )}
          <div className="flex items-center justify-end gap-2">
            <Button variant="outline" onClick={() => setBulkDialog({ open: false, action: '', reason: '', duration: '' })}>Cancel</Button>
            <Button
              variant={bulkDialog.action === 'delete' ? 'destructive' : 'default'}
              disabled={(bulkDialog.action === 'reject' || bulkDialog.action === 'ban') && !bulkDialog.reason}
              onClick={confirmBulkDialog}
            >
              Confirm
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
    </>
  );
}

// API Functions
async function fetchUsers(pagination: PaginationState, sorting: SortingState, filters: ColumnFiltersState, globalFilter: string): Promise<UsersResponse> {
  const params = new URLSearchParams({
    page: String(pagination.pageIndex),
    size: String(pagination.pageSize),
    ...(sorting[0] && { sortBy: sorting[0].id, sortDir: sorting[0].desc ? 'desc' : 'asc' }),
    ...(globalFilter && { search: globalFilter }),
    ...Object.fromEntries(filters.map(f => [f.id, String(f.value)])),
  });

  const response = await apiFetch(`/api/admin/users?${params}`);
  if (!response.ok) throw new Error('Failed to fetch users');
  return response.json();
}

async function createUser(form: any) {
  const response = await apiFetch('/api/admin/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(form),
  });
  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw new Error(err.message || 'Failed to create user');
  }
  return response.json();
}

async function updateUser(id: string, updates: any) {
  const response = await apiFetch(`/api/admin/users/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(updates),
  });
  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw new Error(err.message || 'Failed to update user');
  }
  return response.json();
}

async function approveUser(id: string) {
  const response = await apiFetch(`/api/admin/users/${id}/approve`, { method: 'POST' });
  if (!response.ok) throw new Error('Failed to approve user');
}

async function rejectUser(id: string, reason: string) {
  const response = await apiFetch(`/api/admin/users/${id}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
  });
  if (!response.ok) throw new Error('Failed to reject user');
}

async function banUser(id: string, reason: string, durationDays?: number) {
  const response = await apiFetch(`/api/admin/users/${id}/ban`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason, durationDays }),
  });
  if (!response.ok) throw new Error('Failed to ban user');
}

async function unbanUser(id: string) {
  const response = await apiFetch(`/api/admin/users/${id}/unban`, { method: 'POST' });
  if (!response.ok) throw new Error('Failed to unban user');
}

async function deleteUser(id: string, hard: boolean = false) {
  const response = await apiFetch(`/api/admin/users/${id}?hard=${hard}`, { method: 'DELETE' });
  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw new Error(err.message || 'Failed to delete user');
  }
}

async function bulkAction(action: string, ids: string[], reason?: string, durationDays?: number, hard?: boolean) {
  const response = await apiFetch('/api/admin/users/bulk', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action, ids, reason, durationDays, hard }),
  });
  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw new Error(err.message || 'Bulk action failed');
  }
}