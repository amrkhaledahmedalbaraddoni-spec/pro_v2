'use client';

import { useState } from 'react';
import { Link } from '@tanstack/react-router';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { useTranslation } from 'react-i18next';
import { Shield, Users, Key, Fingerprint, Database, Wifi, ArrowLeft, RotateCcw } from 'lucide-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ErrorBanner, EmptyState, TABS_LIST_CLASS, TABS_TRIGGER_CLASS, TABS_WRAP_CLASS } from './_shared';
import { Dialog } from '@/components/ui/dialog';
import { useAllSessions, useRoles, useApiKeys, useMfaPolicy, useSsoConfig, mutations } from '@/api/queries';

export function SecurityPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<string>('roles');

  // ── الجلسات: حية (GET /api/admin/sessions + terminate) ──
  const sessionsQuery = useAllSessions();
  // ✅ الجلسة 12: بيانات حية للتبويبات — الأدوار والمفاتيح من V68، وMFA/SSO من مخزن الإعدادات
  const rolesQuery = useRoles();
  const apiKeysQuery = useApiKeys();
  const mfaQuery = useMfaPolicy();
  const ssoQuery = useSsoConfig();
  const roles = rolesQuery.data;
  const apiKeys = apiKeysQuery.data;
  const revokeSession = mutations.useRevokeSession();
  const [revokeTarget, setRevokeTarget] = useState<any | null>(null);
  const [revokeReason, setRevokeReason] = useState('');
  const [revokeMsg, setRevokeMsg] = useState<string | null>(null);
  const sessions = sessionsQuery.data ?? [];

  const submitRevoke = async () => {
    if (!revokeTarget || !revokeReason.trim()) return;
    setRevokeMsg(null);
    try {
      await revokeSession.mutateAsync({ sessionId: String(revokeTarget.id), reason: revokeReason.trim() });
      setRevokeMsg(`تم إبطال الجلسة ${revokeTarget.id}`);
      setRevokeTarget(null);
      setRevokeReason('');
      void sessionsQuery.refetch();
    } catch (e: any) {
      setRevokeMsg(`فشل: ${e?.message ?? ''}`);
    }
  };

  return (
    <>
    <div className="space-y-6">
      {/* ✅ الجلسة 12: لافتة شرطية عند فشل الجلسات فقط — البيانات الحية لا تُعلَّم بالوهم */}
      {sessionsQuery.isError && <ErrorBanner message="تعذّر تحميل جلسات المشرفين من الخادم — تبويب الجلسات يعرض حالة خطأ صريحة" />}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('security.title')}</h1>
          <p className="text-foreground/70">{t('security.subtitle')}</p>
        </div>
        <Button asChild variant="outline"><Link to="/security-center">مركز الأمن الحي</Link></Button>
      </div>

      {/* روابط متقاطعة للصفحات التفصيلية */}
      <Card>
        <CardHeader>
          <CardTitle>الصفحات التفصيلية</CardTitle>
          <CardDescription>روابط متقاطعة — نفس البيانات، لا ازدواج منطقي</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline" size="sm"><Link to="/security-roles">Roles</Link></Button>
            <Button asChild variant="outline" size="sm"><Link to="/security-sso">SSO</Link></Button>
            <Button asChild variant="outline" size="sm"><Link to="/security-mfa">MFA</Link></Button>
            <Button asChild variant="outline" size="sm"><Link to="/security-sessions">Sessions</Link></Button>
            <Button asChild variant="outline" size="sm"><Link to="/security-api-keys">API Keys</Link></Button>
          </div>
        </CardContent>
      </Card>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <div className={TABS_WRAP_CLASS}>
        <TabsList className={TABS_LIST_CLASS}>
          <TabsTrigger value="roles" className={TABS_TRIGGER_CLASS}><Shield className="h-4 w-4 mr-2" aria-hidden="true" /> {t('security.roles')}</TabsTrigger>
          <TabsTrigger value="sso" className={TABS_TRIGGER_CLASS}><Wifi className="h-4 w-4 mr-2" aria-hidden="true" /> {t('security.sso')}</TabsTrigger>
          <TabsTrigger value="mfa" className={TABS_TRIGGER_CLASS}><Fingerprint className="h-4 w-4 mr-2" aria-hidden="true" /> {t('security.mfa')}</TabsTrigger>
          <TabsTrigger value="sessions" className={TABS_TRIGGER_CLASS}><Database className="h-4 w-4 mr-2" aria-hidden="true" /> {t('security.sessions')}</TabsTrigger>
          <TabsTrigger value="api-keys" className={TABS_TRIGGER_CLASS}><Key className="h-4 w-4 mr-2" aria-hidden="true" /> {t('security.apiKeys')}</TabsTrigger>
        </TabsList>
        </div>

        <TabsContent value="roles">
          <Card>
            <CardHeader>
              <CardTitle>{t('security.roles')}</CardTitle>
              {/* ✅ الجلسة 12: بيانات حية من /api/admin/security/roles (V68) — إسناد الأدوار من صفحة المستخدمين */}
              <CardDescription>أدوار حية من الخادم — إسناد الأدوار للمستخدمين من صفحة المستخدمين</CardDescription>
            </CardHeader>
            <CardContent>
              {rolesQuery.isLoading && <p className="text-sm text-foreground/70">جار التحميل…</p>}
              {rolesQuery.isError && <EmptyState message="تعذّر تحميل الأدوار من الخادم" />}
              {!rolesQuery.isLoading && !rolesQuery.isError && (roles ?? []).length === 0 && <EmptyState message="لا أدوار معرفة بعد" />}
              {(roles ?? []).length > 0 && (
                <div className="space-y-2">
                  {(roles ?? []).map((r) => (
                    <div key={r.id} className="flex items-center justify-between p-3 border rounded-lg">
                      <div>
                        <p className="font-medium text-foreground">{r.name}{r.isSystem && <Badge variant="outline" className="ml-2">System</Badge>}</p>
                        <p className="text-sm text-foreground/70">{r.description ?? '—'}</p>
                      </div>
                      <Badge variant="blue">{r.permissions.length} permissions</Badge>
                    </div>
                  ))}
                </div>
              )}
              <div className="flex gap-2 mt-4">
                <Button asChild><Link to="/users"><Users className="h-4 w-4 mr-2" aria-hidden="true" /> إدارة المستخدمين والأدوار</Link></Button>
                <Button asChild variant="outline"><Link to="/security-roles">صفحة الأدوار الكاملة</Link></Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="sso">
          <Card>
            <CardHeader>
              <CardTitle>{t('security.sso')}</CardTitle>
              {/* ✅ الجلسة 12: إعداد OIDC حقيقي فوق مخزن V68 — الصفحة التفصيلية للحفظ والاختبار */}
              <CardDescription>إعداد OIDC حقيقي من مخزن الإعدادات — الحفظ والاختبار من الصفحة التفصيلية</CardDescription>
            </CardHeader>
            <CardContent>
              {!ssoQuery.isLoading && !ssoQuery.isError && (
                <div className="space-y-2 mb-4">
                  <p className="text-sm">
                    OIDC: <Badge variant={ssoQuery.data?.oidc?.enabled ? 'success' : 'outline'}>{ssoQuery.data?.oidc?.enabled ? 'Connected' : 'Disconnected'}</Badge>
                    {ssoQuery.data?.oidc?.issuer && <code className="ml-2 text-xs">{ssoQuery.data.oidc.issuer}</code>}
                  </p>
                </div>
              )}
              {ssoQuery.isError && <EmptyState message="تعذّر قراءة إعداد SSO من الخادم" />}
              <Button asChild variant="outline" className="mt-2"><Link to="/security-sso"><ArrowLeft className="h-4 w-4 mr-2" aria-hidden="true" /> صفحة SSO الكاملة</Link></Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="mfa">
          <Card>
            <CardHeader>
              <CardTitle>{t('security.mfa')}</CardTitle>
              {/* ✅ الجلسة 12: سياسة MFA حقيقية فوق مخزن V68 */}
              <CardDescription>سياسة MFA حقيقية من مخزن الإعدادات — الإلزام والطرق المسموحة</CardDescription>
            </CardHeader>
            <CardContent>
              {!mfaQuery.isLoading && !mfaQuery.isError && mfaQuery.data && (
                <div className="space-y-2 mb-4">
                  <p className="text-sm text-foreground/70">
                    إلزام الأدمن: <strong>{mfaQuery.data.enforceForAdmins ? 'نعم' : 'لا'}</strong> • إلزام الجميع: <strong>{mfaQuery.data.enforceForAll ? 'نعم' : 'لا'}</strong>
                  </p>
                  <div className="flex flex-wrap gap-1">
                    {mfaQuery.data.allowedMethods.map((m) => <Badge key={m} variant="blue">{m}</Badge>)}
                  </div>
                </div>
              )}
              {mfaQuery.isError && <EmptyState message="تعذّر قراءة سياسة MFA من الخادم" />}
              <Button asChild variant="outline" className="mt-2"><Link to="/security-mfa">صفحة MFA الكاملة</Link></Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="sessions">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('security.sessions')}</CardTitle>
                <CardDescription>جلسات المشرفين الحية ({sessions.length})</CardDescription>
              </div>
              <Button asChild variant="outline"><Link to="/security-sessions">إدارة كاملة</Link></Button>
            </CardHeader>
            <CardContent>
              {sessionsQuery.isLoading && <p className="text-sm text-foreground/70">جار التحميل…</p>}
              {sessionsQuery.isError && <EmptyState message="تعذر تحميل الجلسات" />}
              {!sessionsQuery.isLoading && !sessionsQuery.isError && sessions.length === 0 && <EmptyState message="لا جلسات نشطة" />}
              {sessions.length > 0 && (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Device</TableHead>
                      <TableHead>IP Address</TableHead>
                      <TableHead>Last Active</TableHead>
                      <TableHead>Trusted</TableHead>
                      <TableHead>Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {sessions.map((s: any) => (
                      <TableRow key={String(s.id)}>
                        <TableCell>{s.deviceName ?? s.deviceId ?? '—'}</TableCell>
                        <TableCell><code>{s.ipAddress ?? '—'}</code></TableCell>
                        <TableCell>{s.lastActivityAt ?? '—'}</TableCell>
                        <TableCell><Badge variant={s.isTrusted ? 'success' : 'outline'}>{s.isTrusted ? 'Trusted' : 'Untrusted'}</Badge></TableCell>
                        <TableCell>
                          <Button variant="ghost" size="icon" className="text-red-500" aria-label={`Revoke session ${s.id}`} onClick={() => { setRevokeTarget(s); setRevokeReason(''); setRevokeMsg(null); }}>
                            <RotateCcw className="h-4 w-4" aria-hidden="true" />
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
              {revokeMsg && <p role="status" className="mt-2 text-sm font-medium text-foreground">{revokeMsg}</p>}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="api-keys">
          <Card>
            <CardHeader>
              <CardTitle>{t('security.apiKeys')}</CardTitle>
              {/* ✅ الجلسة 12: مفاتيح API حية من /api/admin/security/api-keys (V68) */}
              <CardDescription>مفاتيح حية من الخادم — الإنشاء والإبطال من الصفحة التفصيلية</CardDescription>
            </CardHeader>
            <CardContent>
              {apiKeysQuery.isLoading && <p className="text-sm text-foreground/70">جار التحميل…</p>}
              {apiKeysQuery.isError && <EmptyState message="تعذّر تحميل مفاتيح API من الخادم" />}
              {!apiKeysQuery.isLoading && !apiKeysQuery.isError && (apiKeys ?? []).length === 0 && <EmptyState message="لا مفاتيح API بعد — أنشئ أول مفتاح من الصفحة التفصيلية" />}
              {(apiKeys ?? []).length > 0 && (
                <div className="space-y-2">
                  {(apiKeys ?? []).slice(0, 5).map((k) => (
                    <div key={k.id} className="flex items-center justify-between p-3 border rounded-lg">
                      <div>
                        <p className="font-medium text-foreground">{k.name}</p>
                        <p className="text-xs text-foreground/60 font-mono">{k.keyPrefix}…</p>
                      </div>
                      <Badge variant={k.isActive ? 'success' : 'danger'}>{k.isActive ? 'Active' : 'Revoked'}</Badge>
                    </div>
                  ))}
                  {(apiKeys ?? []).length > 5 && <p className="text-xs text-foreground/60">+{(apiKeys ?? []).length - 5} أخرى</p>}
                </div>
              )}
              <Button asChild variant="outline" className="mt-4"><Link to="/security-api-keys">صفحة المفاتيح الكاملة</Link></Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <Dialog open={revokeTarget !== null} onOpenChange={(o) => { if (!o) setRevokeTarget(null); }} title="إبطال الجلسة">
        <p className="text-sm text-foreground/70 mb-4">إبطال جلسة <code>{revokeTarget?.deviceName ?? revokeTarget?.id}</code> — اذكر السبب (يُسجل في التدقيق):</p>
        <Input value={revokeReason} onChange={(e) => setRevokeReason(e.target.value)} placeholder="السبب" aria-label="Revoke reason" />
        <div className="flex justify-end gap-2 mt-4">
          <Button variant="outline" onClick={() => setRevokeTarget(null)}>إلغاء</Button>
          <Button disabled={!revokeReason.trim() || revokeSession.isPending} onClick={() => void submitRevoke()}>
            {revokeSession.isPending ? 'جار الإبطال…' : 'إبطال'}
          </Button>
        </div>
      </Dialog>
    </div>
    </>
  );
}
