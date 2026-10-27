'use client';

import { useEffect, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useTranslation } from 'react-i18next';
import { User, Settings, Activity, Smartphone, Monitor, Edit, Key, Shield } from 'lucide-react';
import { useAuth } from '@/components/providers/AuthProvider';
import { useNavigate } from '@tanstack/react-router';
import { EmptyState, TABS_LIST_CLASS, TABS_TRIGGER_CLASS, TABS_WRAP_CLASS } from './_shared';
import { apiFetch, getAuditLog } from '@/api';
import { useAllSessions } from '@/api/queries';

export function ProfilePage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<string>('profile');
  const [savedAt, setSavedAt] = useState<string | null>(null);
  // ✅ الجلسة 12: حفظ حقيقي — PATCH /api/auth/profile يدعم displayName وbio فقط
  const [displayNameDraft, setDisplayNameDraft] = useState(user?.displayName ?? '');
  const [bioDraft, setBioDraft] = useState('');
  const bioInitial = '';
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileError, setProfileError] = useState('');

  const saveProfile = async () => {
    setSavingProfile(true);
    setProfileError('');
    try {
      const res = await apiFetch('/api/auth/profile', {
        method: 'PATCH',
        body: JSON.stringify({ displayName: displayNameDraft, bio: bioDraft || null }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => null);
        throw new Error(String((data as any)?.error ?? `HTTP ${res.status}`));
      }
      setSavedAt(new Date().toLocaleTimeString());
    } catch (e: any) {
      setProfileError(e?.message ?? 'خطأ غير متوقع');
    } finally {
      setSavingProfile(false);
    }
  };

  // ── جلساتي ونشاطي: حية من الخادم ──
  const sessionsQuery = useAllSessions();
  const [auditItems, setAuditItems] = useState<any[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  const mySessions = (sessionsQuery.data ?? []).filter((s: any) => !user?.id || user.id === 'unknown' || String(s.userId) === String(user.id) || String(s.adminId) === String(user.id));

  useEffect(() => {
    if (activeTab !== 'activity' || !user?.id || user.id === 'unknown') return;
    let alive = true;
    (async () => {
      setAuditLoading(true);
      try {
        const res: any = await getAuditLog({ adminId: String(user.id), size: 20 });
        const items = Array.isArray(res) ? res : res?.content ?? [];
        if (alive) setAuditItems(items);
      } catch {
        if (alive) setAuditItems([]);
      } finally {
        if (alive) setAuditLoading(false);
      }
    })();
    return () => { alive = false; };
  }, [activeTab, user?.id]);

  return (
    <>
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('profile.title')}</h1>
        <p className="text-foreground/70">{t('profile.subtitle')}</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-4">
        {/* Sidebar Profile */}
        <Card className="lg:col-span-1">
          <CardContent className="p-6 text-center">
            <Avatar className="h-24 w-24 mx-auto mb-4">
              <AvatarImage src={user?.avatarUrl} alt={user?.displayName || user?.username} />
              <AvatarFallback className="text-3xl">
                {(user?.displayName || user?.username || 'U').charAt(0).toUpperCase()}
              </AvatarFallback>
            </Avatar>
            <h2 className="text-xl font-bold text-foreground">{user?.displayName || user?.username || '—'}</h2>
            <p className="text-foreground/70">@{user?.username || '—'}</p>
            <Badge variant="outline" className="mt-2">{user?.role || 'USER'}</Badge>
            <div className="mt-4 pt-4 border-t space-y-2">
              <Button variant="outline" className="w-full" onClick={() => setActiveTab('profile')}><Settings className="h-4 w-4 mr-2" aria-hidden="true" /> {t('profile.editProfile')}</Button>
              <Button variant="outline" className="w-full" disabled title="تغيير كلمة المرور غير مدعوم في الخادم" aria-label="تغيير كلمة المرور (غير مدعوم في الخادم)"><Key className="h-4 w-4 mr-2" aria-hidden="true" /> {t('profile.changePassword')}</Button>
              <Button variant="outline" className="w-full" onClick={() => navigate({ to: '/settings' })}><Shield className="h-4 w-4 mr-2" aria-hidden="true" /> {t('profile.securitySettings')}</Button>
            </div>
          </CardContent>
        </Card>

        {/* Main Content */}
        <div className="lg:col-span-3 space-y-6">
          <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
            <div className={TABS_WRAP_CLASS}>
            <TabsList className={TABS_LIST_CLASS}>
              <TabsTrigger value="profile" className={TABS_TRIGGER_CLASS}><User className="h-4 w-4 mr-2" aria-hidden="true" /> {t('profile.tabProfile')}</TabsTrigger>
              <TabsTrigger value="devices" className={TABS_TRIGGER_CLASS}><Smartphone className="h-4 w-4 mr-2" aria-hidden="true" /> {t('profile.tabDevices')}</TabsTrigger>
              <TabsTrigger value="sessions" className={TABS_TRIGGER_CLASS}><Monitor className="h-4 w-4 mr-2" aria-hidden="true" /> {t('profile.tabSessions')}</TabsTrigger>
              <TabsTrigger value="activity" className={TABS_TRIGGER_CLASS}><Activity className="h-4 w-4 mr-2" aria-hidden="true" /> {t('profile.tabActivity')}</TabsTrigger>
            </TabsList>
            </div>

            <TabsContent value="profile">
              <Card>
                <CardHeader>
                  <CardTitle>{t('profile.info')}</CardTitle>
                  {/* ✅ الجلسة 12: حفظ حقيقي عبر PATCH /api/auth/profile (displayName/bio) — كان زرًا محليًا فقط */}
                  <CardDescription>الاسم المعروض يُحفظ في الخادم؛ اسم المستخدم والبريد لا يعدّلهما هذا المسار</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4 max-w-md">
                  <div className="grid gap-4 md:grid-cols-2">
                    <div className="space-y-2">
                      <Label htmlFor="profile-display-name">{t('profile.displayName')}</Label>
                      <Input id="profile-display-name" value={displayNameDraft} onChange={(e) => setDisplayNameDraft(e.target.value)} aria-label={t('profile.displayName')} />
                    </div>
                    <div className="space-y-2">
                      <Label>{t('profile.username')}</Label>
                      <Input defaultValue={user?.username ?? ''} disabled aria-label={t('profile.username')} />
                    </div>
                    <div className="space-y-2">
                      <Label>{t('profile.email')}</Label>
                      <Input type="email" defaultValue={user?.email ?? ''} disabled aria-label={t('profile.email')} />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="profile-bio">Bio</Label>
                      <Input id="profile-bio" value={bioDraft} onChange={(e) => setBioDraft(e.target.value)} aria-label="Bio" />
                    </div>
                  </div>
                  <Button
                    disabled={savingProfile || (displayNameDraft === (user?.displayName ?? '') && bioDraft === bioInitial)}
                    onClick={() => void saveProfile()}
                  >
                    <Edit className="h-4 w-4 mr-2" aria-hidden="true" />
                    {savingProfile ? 'جار الحفظ…' : t('profile.save')}
                  </Button>
                  {profileError && <p role="alert" className="text-sm text-red-500">فشل الحفظ: {profileError}</p>}
                  {savedAt && !profileError && (
                    <p role="status" className="text-sm font-medium text-emerald-600">{t('common.success')} • {savedAt} • حُفظ في الخادم عبر PATCH /api/auth/profile</p>
                  )}
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="devices">
              <Card>
                <CardHeader>
                  <CardTitle>{t('profile.trustedDevices')}</CardTitle>
                </CardHeader>
                <CardContent>
                  <EmptyState message="لا يوجد مسار أجهزة المشرف في الخادم — الجلسات الحية في الأسفل" />
                  <Button asChild variant="outline" className="mt-4"><Link to="/security-sessions">جلساتي الحية</Link></Button>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="sessions">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between">
                  <div>
                    <CardTitle>{t('profile.activeSessions')}</CardTitle>
                  </div>
                  <Button asChild variant="outline" size="sm"><Link to="/security-sessions">إدارة الجلسات</Link></Button>
                </CardHeader>
                <CardContent>
                  {sessionsQuery.isLoading && <p className="text-sm text-foreground/70">جار التحميل…</p>}
                  {!sessionsQuery.isLoading && mySessions.length === 0 && <EmptyState message="لا جلسات مسجلة لحسابك" />}
                  <div className="space-y-3">
                    {mySessions.map((s: any) => (
                      <div key={String(s.id)} className="flex items-center justify-between p-4 border rounded-lg">
                        <div className="flex items-center gap-4">
                          <Monitor className="h-6 w-6 text-foreground/70" aria-hidden="true" />
                          <div>
                            <p className="font-medium text-foreground">{s.deviceName ?? s.deviceId ?? '—'}</p>
                            <p className="text-sm text-foreground/70">{s.ipAddress ?? '—'}</p>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-sm text-foreground/70">{s.lastActivityAt ?? ''}</span>
                          <Badge variant={s.isTrusted ? 'success' : 'outline'}>{s.isTrusted ? 'Trusted' : 'Untrusted'}</Badge>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="activity">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between">
                  <div>
                    <CardTitle>{t('profile.tabActivity')}</CardTitle>
                  </div>
                  <Button asChild variant="outline" size="sm"><Link to="/audit">سجل التدقيق الكامل</Link></Button>
                </CardHeader>
                <CardContent>
                  {auditLoading && <p className="text-sm text-foreground/70">جار التحميل…</p>}
                  {!auditLoading && auditItems.length === 0 && <EmptyState message="لا نشاط مسجل لحسابك بعد" />}
                  <div className="space-y-3">
                    {auditItems.map((a: any, i: number) => (
                      <div key={String(a.id ?? i)} className="flex items-center justify-between p-4 border rounded-lg">
                        <div className="flex items-center gap-4">
                          <Activity className="h-6 w-6 text-primary" aria-hidden="true" />
                          <div>
                            <p className="font-medium text-foreground">{a.action ?? a.category ?? '—'}</p>
                            <p className="text-sm text-foreground/70">{a.details ?? a.description ?? ''}</p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p className="text-sm text-foreground/70">{a.createdAt ?? a.timestamp ?? ''}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </div>
      </div>
    </div>
    </>
  );
}