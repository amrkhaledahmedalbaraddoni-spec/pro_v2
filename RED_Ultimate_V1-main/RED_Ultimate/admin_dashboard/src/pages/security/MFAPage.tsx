import { useEffect, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { Fingerprint, Key, Smartphone, RefreshCw, Save } from 'lucide-react';
import { useMfaPolicy, mutations, type MfaPolicy } from '@/api/queries';
import { useTranslation } from 'react-i18next';
import { cn } from '@/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Tabs } from '@/components/ui/tabs';
import { ErrorBanner, ErrorState, LoadingState } from '@/pages/_shared';

// ✅ الجلسة 12: سياسة MFA حقيقية — تُقرأ وتُكتب من الخادم (مخزن admin_system_config، مفاتيح mfa.*).
// بلا بيانات وهمية: الفشل حالة خطأ صريحة، والتعديلات تُحفظ عبر PUT /api/admin/mfa/policy بتدقيق إلزامي.

const ALL_METHODS = [
  { value: 'TOTP', label: 'TOTP', hint: 'Time-based One-Time Password (Google Authenticator, etc.)' },
  { value: 'WEB_AUTHN', label: 'WEB_AUTHN', hint: 'WebAuthn / Passkeys (Touch ID, Face ID, Security Keys)' },
  { value: 'BACKUP_CODES', label: 'BACKUP_CODES', hint: 'One-time backup codes for account recovery' },
  { value: 'SMS', label: 'SMS', hint: 'SMS-based verification codes' },
  { value: 'EMAIL', label: 'EMAIL', hint: 'Email-based verification codes' },
];

const METHOD_ICON: Record<string, React.ComponentType<{ className?: string }>> = {
  TOTP: Fingerprint,
  WEB_AUTHN: Smartphone,
  BACKUP_CODES: Key,
};

export function MFAPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('settings');
  const [draft, setDraft] = useState<MfaPolicy | null>(null);
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const [saveError, setSaveError] = useState('');

  const policyQuery = useMfaPolicy();
  const updateMutation = mutations.useUpdateMfaPolicy();

  useEffect(() => {
    if (policyQuery.data && draft === null) setDraft(policyQuery.data);
  }, [policyQuery.data, draft]);

  const tabs = [
    { value: 'settings', label: 'MFA Settings' },
    { value: 'methods', label: 'Auth Methods' },
    { value: 'enrollment', label: 'Enrollment' },
  ];

  const save = async () => {
    if (!draft) return;
    setSaveError('');
    try {
      const saved = await updateMutation.mutateAsync(draft);
      setDraft(saved);
      policyQuery.refetch();
      setSavedAt(new Date().toLocaleTimeString());
    } catch (e: any) {
      setSaveError(e?.response?.data?.error ?? e?.message ?? 'فشل الحفظ');
    }
  };

  if (policyQuery.isLoading && !policyQuery.data) {
    return (
      <div className="yn-page space-y-6">
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="secondary" size="sm"><Link to="/security">عودة إلى Security</Link></Button>
        </div>
        <LoadingState label="جار قراءة سياسة MFA من الخادم…" />
      </div>
    );
  }

  if (policyQuery.isError && !draft) {
    return (
      <div className="yn-page space-y-6">
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="secondary" size="sm"><Link to="/security">عودة إلى Security</Link></Button>
        </div>
        <ErrorState
          message="تعذّر قراءة سياسة MFA من الخادم — تحقق من الاتصال والصلاحية"
          onRetry={() => policyQuery.refetch()}
        />
      </div>
    );
  }

  const policy = draft ?? policyQuery.data;
  if (!policy) return null;

  const toggleMethod = (method: string) => {
    const has = policy.allowedMethods.includes(method);
    const next = has ? policy.allowedMethods.filter((m) => m !== method) : [...policy.allowedMethods, method];
    if (next.length === 0) return; // طريقة واحدة على الأقل إلزامية (يرفضها الخادم أيضًا)
    setDraft({ ...policy, allowedMethods: next });
  };

  const dirty = JSON.stringify(policy) !== JSON.stringify(policyQuery.data ?? {});

  return (
    <div className="yn-page space-y-6">
      {/* ✅ 2026-09-26: زر عودة للصفحة العامة — دمج الازدواج الملاحي بدون حذف */}
      <div className="flex flex-wrap gap-2">
        <Button asChild variant="secondary" size="sm"><Link to="/security">عودة إلى Security</Link></Button>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('security.mfa.title')}</h1>
          <p className="text-yn-text-secondary">{t('security.mfa.subtitle')}</p>
          <p className="text-xs text-yn-text-muted mt-1">
            سياسة حقيقية من الخادم (مخزن V68) — تُحفظ عبر PUT /api/admin/mfa/policy بتدقيق MFA_POLICY_UPDATED
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="secondary" size="sm" onClick={() => policyQuery.refetch()}>
            <RefreshCw className={cn('w-4 h-4 mr-2', policyQuery.isLoading && 'animate-spin')} />
            Refresh
          </Button>
          <Button variant="primary" size="sm" disabled={!dirty || updateMutation.isPending} onClick={() => void save()}>
            <Save className="w-4 h-4 mr-2" />
            {updateMutation.isPending ? 'Saving…' : 'Save Changes'}
          </Button>
        </div>
      </div>

      {policyQuery.isError && (
        <ErrorBanner message="آخر قراءة للسياسة فشلت — القيم المعروضة آخر حفظ ناجح؛ حدّث قبل التعديل" />
      )}
      {dirty && (
        <div role="status" className="flex items-center gap-2 rounded-lg border border-sky-600/40 bg-sky-500/10 px-4 py-2 text-sm">
          تغييرات غير محفوظة — اضغط Save Changes لإرسالها للخادم
        </div>
      )}
      {saveError && <p role="alert" className="text-sm text-red-500">فشل الحفظ: {saveError}</p>}
      {savedAt && !dirty && !saveError && (
        <p role="status" className="text-sm text-emerald-500">حُفظت السياسة في الخادم • {savedAt}</p>
      )}

      <Tabs value={activeTab} onChange={setActiveTab} tabs={tabs} />

      {activeTab === 'settings' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card title="MFA Enforcement" subtitle="Configure when MFA is required">
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-yn-text">Enforce for Admins</p>
                  <p className="text-sm text-yn-text-secondary">Require MFA for all admin accounts</p>
                </div>
                <button
                  type="button"
                  aria-label="Enforce for Admins"
                  className={cn('relative w-12 h-7 rounded-full transition-colors', policy.enforceForAdmins ? 'bg-yn-green' : 'bg-yn-border')}
                  onClick={() => setDraft({ ...policy, enforceForAdmins: !policy.enforceForAdmins })}
                >
                  <span className={cn('absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform', policy.enforceForAdmins ? 'translate-x-6' : 'translate-x-0.5')} />
                </button>
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-yn-text">Enforce for All Users</p>
                  <p className="text-sm text-yn-text-secondary">Require MFA for all user accounts</p>
                </div>
                <button
                  type="button"
                  aria-label="Enforce for All Users"
                  className={cn('relative w-12 h-7 rounded-full transition-colors', policy.enforceForAll ? 'bg-yn-green' : 'bg-yn-border')}
                  onClick={() => setDraft({ ...policy, enforceForAll: !policy.enforceForAll })}
                >
                  <span className={cn('absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform', policy.enforceForAll ? 'translate-x-6' : 'translate-x-0.5')} />
                </button>
              </div>

              <div className="pt-4 border-t border-yn-border">
                <p className="font-medium text-yn-text mb-3">TOTP Time Window</p>
                <div className="flex items-center gap-4">
                  <input
                    type="number"
                    value={policy.totpWindow}
                    min={0}
                    max={10}
                    className="yn-input w-24"
                    aria-label="TOTP window"
                    onChange={(e) => {
                      const v = Number(e.target.value);
                      if (Number.isFinite(v)) setDraft({ ...policy, totpWindow: Math.max(0, Math.min(10, Math.trunc(v))) });
                    }}
                  />
                  <span className="text-yn-text-secondary">time steps (±) — 0..10</span>
                </div>
              </div>
            </div>
          </Card>

          <Card title="Allowed Methods" subtitle="Configure which MFA methods are available">
            <div className="space-y-4">
              {ALL_METHODS.map((method) => {
                const enabled = policy.allowedMethods.includes(method.value);
                const Icon = METHOD_ICON[method.value] ?? Key;
                return (
                  <div key={method.value} className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-yn-green/15 flex items-center justify-center">
                        <Icon className="w-4 h-4 text-yn-green" />
                      </div>
                      <div>
                        <p className="font-medium text-yn-text">{method.label}</p>
                        <p className="text-sm text-yn-text-secondary">{method.hint}</p>
                      </div>
                    </div>
                    <button
                      type="button"
                      aria-label={`Toggle ${method.label}`}
                      className={cn('relative w-12 h-7 rounded-full transition-colors', enabled ? 'bg-yn-green' : 'bg-yn-border')}
                      onClick={() => toggleMethod(method.value)}
                    >
                      <span className={cn('absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform', enabled ? 'translate-x-6' : 'translate-x-0.5')} />
                    </button>
                  </div>
                );
              })}
            </div>
          </Card>
        </div>
      )}

      {activeTab === 'methods' && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {ALL_METHODS.filter((m) => ['TOTP', 'WEB_AUTHN', 'BACKUP_CODES'].includes(m.value)).map((method) => {
            const Icon = METHOD_ICON[method.value] ?? Key;
            const enabled = policy.allowedMethods.includes(method.value);
            return (
              <MethodCard
                key={method.value}
                title={method.label}
                description={method.hint}
                icon={Icon}
                enabled={enabled}
                onToggle={() => toggleMethod(method.value)}
              />
            );
          })}
        </div>
      )}

      {activeTab === 'enrollment' && (
        <div className="yn-card yn-card-liquid yn-glass p-6">
          <div className="text-center py-12 text-yn-text-muted">
            <Fingerprint className="w-12 h-12 mx-auto mb-4 text-yn-text-muted/50" />
            <h3 className="font-medium text-yn-text mb-2">MFA Enrollment Management</h3>
            <p>تسجيل TOTP الفعلي للمستخدمين يتطلب تدفق دخول جديد في الباك اند (مشروع مستقل موثق في الخطة) — هذه الصفحة تدير السياسة المطبقة فقط.</p>
          </div>
        </div>
      )}
    </div>
  );
}

function MethodCard({ title, description, icon: Icon, enabled, onToggle }: { title: string; description: string; icon: React.ComponentType<{ className?: string }>; enabled: boolean; onToggle: () => void }) {
  return (
    <div className={cn('yn-card yn-card-liquid yn-glass p-6', enabled ? 'border-yn-green/30' : '')}>
      <div className="flex items-start justify-between mb-4">
        <div className="w-12 h-12 rounded-xl bg-yn-green/15 flex items-center justify-center">
          <Icon className="w-6 h-6 text-yn-green" />
        </div>
        <Badge variant={enabled ? 'green' : 'default'}>{enabled ? 'Enabled' : 'Disabled'}</Badge>
      </div>
      <h3 className="font-medium text-yn-text mb-1">{title}</h3>
      <p className="text-sm text-yn-text-secondary mb-4">{description}</p>

      <div className="mt-4 flex gap-2">
        <Button variant={enabled ? 'secondary' : 'primary'} size="sm" className="flex-1" onClick={onToggle}>
          {enabled ? 'Disable' : 'Enable'}
        </Button>
      </div>
    </div>
  );
}

export default MFAPage;
