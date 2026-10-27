import { useEffect, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { Save, TestTube, Download, Shield, ExternalLink } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { ErrorBanner } from '@/pages/_shared';
import { cn } from '@/utils';
import { apiFetch } from '../../api';
import { mutations, type SsoTestResult } from '@/api/queries';
import { Button } from '@/components/ui/button';
import { Form, FormField } from '@/components/ui/form';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Tabs } from '@/components/ui/tabs';

interface SSOConfig {
  oidc?: { enabled: boolean; issuer?: string; clientId?: string; scopes?: string[]; redirectUri?: string; logoutUri?: string; provider?: string; lastSync?: string };
  saml?: { enabled: boolean };
}

export function SSOPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('oidc');
  const [config, setConfig] = useState<SSOConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [oidcEnabled, setOidcEnabled] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<SsoTestResult | null>(null);
  const [testing, setTesting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError('');
      try {
        const res = await apiFetch('/api/admin/security/sso');
        const data = await res.json().catch(() => null);
        if (!res.ok) throw new Error((data as any)?.error || `HTTP ${res.status}`);
        if (!cancelled) {
          setConfig(data && typeof data === 'object' ? data : null);
          setOidcEnabled(Boolean((data as any)?.oidc?.enabled));
        }
      } catch (e: any) {
        if (!cancelled) {
          setConfig(null);
          setError(e?.message || 'تعذر جلب إعدادات SSO من الخادم');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, []);

  // ✅ الجلسة 12: حفظ حقيقي — PUT /api/admin/security/sso (تدقيق SSO_CONFIG_UPDATED؛ clientSecret يُكتب ولا يُعاد)
  const saveMutation = mutations.useUpdateSsoConfig();
  const saveSso = async (data: any) => {
    setSaveError('');
    try {
      const payload: any = { enabled: oidcEnabled };
      if (data.issuer) payload.issuer = data.issuer;
      if (data.clientId) payload.clientId = data.clientId;
      if (data.clientSecret) payload.clientSecret = data.clientSecret;
      if (data.scopes) payload.scopes = String(data.scopes).split(/[\n,]/).map((s: string) => s.trim()).filter(Boolean);
      if (data.redirectUri) payload.redirectUri = data.redirectUri;
      if (data.logoutUri) payload.logoutUri = data.logoutUri;
      const saved = await saveMutation.mutateAsync(payload);
      setConfig((prev) => ({ ...(prev ?? {}), ...saved }));
      setOidcEnabled(Boolean(saved?.oidc?.enabled));
      setSavedAt(new Date().toLocaleTimeString());
    } catch (e: any) {
      setSaveError(e?.response?.data?.error ?? e?.message ?? 'فشل الحفظ');
    }
  };

  // ✅ الجلسة 12: اختبار اكتشاف OIDC حقيقي — يجلب .well-known/openid-configuration ويتحقق من الحقول
  const runTest = async (issuer: string) => {
    if (!issuer) { setTestResult({ success: false, reachable: false, error: 'ISSUER_REQUIRED' }); return; }
    setTesting(true);
    setTestResult(null);
    try {
      const res = await apiFetch('/api/admin/security/sso/test/' + encodeURIComponent(issuer), {
        method: 'PUT',
        body: JSON.stringify({ issuer }),
      });
      const data = (await res.json().catch(() => null)) as SsoTestResult | null;
      setTestResult(data ?? { success: false, reachable: false, error: 'INVALID_RESPONSE' });
    } catch (e: any) {
      setTestResult({ success: false, reachable: false, error: e?.message ?? 'NETWORK_ERROR' });
    } finally {
      setTesting(false);
    }
  };

  const tabs = [
    { value: 'oidc', label: t('security.sso.oidc') },
    { value: 'saml', label: t('security.sso.saml') },
    { value: 'test', label: 'Test Connection' },
  ];

  const oidc = config?.oidc;

  return (
    <div className="yn-page space-y-6">
      {/* ✅ 2026-09-26: زر عودة للصفحة العامة — دمج الازدواج الملاحي بدون حذف */}
      <div className="flex flex-wrap gap-2">
        <Button asChild variant="secondary" size="sm"><Link to="/security">عودة إلى Security</Link></Button>
      </div>

      {!!error && <ErrorBanner message={`تعذّر قراءة إعداد SSO من الخادم: ${error} — ما تراه أدناه وهمي`} />}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('security.sso.title')}</h1>
          <p className="text-yn-text-secondary">{t('security.sso.subtitle')}</p>
          <p className="text-xs text-yn-text-muted mt-1">إعدادات الدخول الموحد تُجلب من الخادم — لا توجد قيم وهمية مخزنة محلياً.</p>
        </div>
      </div>

      <Tabs value={activeTab} onChange={setActiveTab} tabs={tabs} />

      {loading && <div className="yn-loading"><div className="yn-spinner" /></div>}

      {!loading && error && (
        <div className="yn-card yn-card-liquid yn-glass p-6 text-center">
          <Shield className="w-10 h-10 mx-auto mb-3 text-yn-text-muted/50" />
          <p className="font-medium text-yn-text mb-1">لا توجد إعدادات معروضة</p>
          <p className="text-sm text-yn-text-secondary">تُجلب من الخادم عبر <span className="font-mono">GET /api/admin/security/sso</span></p>
          <p className="text-xs text-yn-error mt-2">{error}</p>
        </div>
      )}

      {!loading && !error && !oidc && activeTab !== 'test' && (
        <div className="yn-card yn-card-liquid yn-glass p-6 text-center">
          <Shield className="w-10 h-10 mx-auto mb-3 text-yn-text-muted/50" />
          <p className="font-medium text-yn-text mb-1">لا يوجد إعداد SSO بعد</p>
          <p className="text-sm text-yn-text-secondary">تُجلب من الخادم — الحالة فارغة حتى يضيف المسؤول موفر هوية.</p>
        </div>
      )}

      {!loading && !error && activeTab === 'oidc' && oidc && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <Card title="OIDC Configuration" subtitle="OpenID Connect settings (Keycloak/Auth0)">
              <Form onSubmit={(data: any) => { void saveSso(data); }} initialValues={{ issuer: oidc.issuer || '', clientId: oidc.clientId || '', redirectUri: oidc.redirectUri || '', logoutUri: oidc.logoutUri || '' }}>
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <p className="font-medium text-yn-text">OIDC Provider</p>
                    <p className="text-sm text-yn-text-secondary">Enable or disable OIDC authentication</p>
                  </div>
                  <button
                    type="button"
                    className={cn('relative w-12 h-7 rounded-full transition-colors', oidcEnabled ? 'bg-yn-green' : 'bg-yn-border')}
                    onClick={() => setOidcEnabled((v) => !v)}
                  >
                    <span className={cn('absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform', oidcEnabled ? 'translate-x-6' : 'translate-x-0.5')} />
                  </button>
                </div>

                <FormField name="issuer" label="Issuer URL" placeholder="https://keycloak.example.com/realms/master" />
                <FormField name="clientId" label="Client ID" placeholder="admin-dashboard" />
                <FormField name="clientSecret" label="Client Secret" type="password" placeholder="••••••••••••••••" />
                <FormField name="scopes" label="Scopes" type="textarea" placeholder="openid, profile, email, roles" />
                <FormField name="redirectUri" label="Redirect URI" placeholder="https://admin.example.com/callback" />
                <FormField name="logoutUri" label="Logout URI" placeholder="https://admin.example.com/logout" />

                {saveError && <p role="alert" className="text-sm text-yn-error mt-3">فشل الحفظ: {saveError}</p>}
                {savedAt && !saveError && <p role="status" className="text-sm text-yn-green mt-3">حُفظ الإعداد في الخادم • {savedAt}</p>}
                <div className="flex gap-3 pt-4 border-t border-yn-border">
                  <Button variant="primary" type="submit" disabled={saveMutation.isPending}>
                    <Save className="w-4 h-4 mr-2" />
                    {saveMutation.isPending ? 'Saving…' : 'Save Configuration'}
                  </Button>
                  <Button type="button" variant="secondary" disabled={testing} onClick={() => { void runTest(oidc.issuer || ''); setActiveTab('test'); }}>
                    <TestTube className={cn('w-4 h-4 mr-2', testing && 'animate-pulse')} />
                    {testing ? 'Testing…' : 'Test Connection'}
                  </Button>
                </div>
              </Form>
            </Card>

            <Card title="Attribute Mapping" subtitle="Map OIDC claims to user attributes">
              <Form onSubmit={() => {}} initialValues={{}}>
                <div className="grid grid-cols-2 gap-4">
                  <FormField name="usernameClaim" label="Username Claim" placeholder="preferred_username" />
                  <FormField name="emailClaim" label="Email Claim" placeholder="email" />
                  <FormField name="displayNameClaim" label="Display Name Claim" placeholder="name" />
                  <FormField name="rolesClaim" label="Roles Claim" placeholder="realm_access.roles" />
                </div>
              </Form>
            </Card>
          </div>

          <div className="space-y-4">
            <Card title="Status" subtitle="Connection status — تُجلب من الخادم">
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-yn-text-secondary">Status</span>
                  <Badge variant={oidc.enabled ? 'green' : 'default'}>
                    {oidc.enabled ? 'Connected' : 'Disconnected'}
                  </Badge>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-yn-text-secondary">Provider</span>
                  <span className="font-mono text-yn-text-secondary text-sm">{oidc.provider || '—'}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-yn-text-secondary">Last Sync</span>
                  <span className="text-yn-text-secondary">{oidc.lastSync || '—'}</span>
                </div>
              </div>
            </Card>

            <Card title="Actions" subtitle="Quick actions">
              <div className="space-y-2">
                <Button variant="secondary" className="w-full justify-start">
                  <TestTube className="w-4 h-4 mr-2" />
                  Test Connection
                </Button>
                <Button variant="secondary" className="w-full justify-start">
                  <Download className="w-4 h-4 mr-2" />
                  Download Metadata
                </Button>
                <Button variant="secondary" className="w-full justify-start">
                  <ExternalLink className="w-4 h-4 mr-2" />
                  View Discovery Document
                </Button>
              </div>
            </Card>
          </div>
        </div>
      )}

      {!loading && !error && activeTab === 'saml' && (
        <div className="yn-card yn-card-liquid yn-glass p-6">
          <div className="text-center py-12 text-yn-text-muted">
            <Shield className="w-12 h-12 mx-auto mb-4 text-yn-text-muted/50" />
            <h3 className="font-medium text-yn-text mb-2">SAML Configuration</h3>
            <p>تُجلب من الخادم — لا يوجد إعداد SAML بعد (حالة فارغة).</p>
          </div>
        </div>
      )}

      {activeTab === 'test' && (
        <div className="yn-card yn-card-liquid yn-glass p-6">
          <div className="max-w-xl mx-auto">
            <h3 className="font-medium text-yn-text mb-4">Test SSO Connection</h3>
            <p className="text-sm text-yn-text-secondary mb-4">
              اختبار حقيقي: يجلب <span className="font-mono">.well-known/openid-configuration</span> من الـ issuer ويتحقق من صحة JSON وحقول النهايات — لا ادعاء نجاح.
            </p>
            <Form onSubmit={(data: any) => { void runTest(data.url || oidc?.issuer || ''); }} initialValues={{ url: oidc?.issuer || '' }}>
              <FormField name="url" label="Issuer URL" placeholder="https://keycloak.example.com/realms/master" required />
              <Button variant="primary" type="submit" className="w-full" disabled={testing}>
                <TestTube className={cn('w-4 h-4 mr-2', testing && 'animate-pulse')} />
                {testing ? 'Running…' : 'Run Test'}
              </Button>
            </Form>

            {testResult && (
              <div className={cn('mt-4 p-4 rounded-lg border', testResult.success ? 'bg-yn-green/10 border-yn-green/30' : 'bg-yn-error/10 border-yn-error/30')}>
                <p className="font-medium mb-2">{testResult.success ? '✅ الاكتشاف نجح' : '❌ الاكتشاف فشل'}</p>
                <dl className="text-sm space-y-1">
                  <div><dt className="inline text-yn-text-secondary">reachable: </dt><dd className="inline font-mono">{String(testResult.reachable)}</dd></div>
                  {testResult.discoveryUrl && <div><dt className="inline text-yn-text-secondary">discoveryUrl: </dt><dd className="inline font-mono text-xs break-all">{testResult.discoveryUrl}</dd></div>}
                  {testResult.error && <div><dt className="inline text-yn-text-secondary">error: </dt><dd className="inline font-mono">{testResult.error}</dd></div>}
                  {testResult.detail && <div><dt className="inline text-yn-text-secondary">detail: </dt><dd className="inline font-mono text-xs">{testResult.detail}</dd></div>}
                  {testResult.authorizationEndpoint && <div><dt className="inline text-yn-text-secondary">authorization: </dt><dd className="inline font-mono text-xs break-all">{testResult.authorizationEndpoint}</dd></div>}
                  {testResult.tokenEndpoint && <div><dt className="inline text-yn-text-secondary">token: </dt><dd className="inline font-mono text-xs break-all">{testResult.tokenEndpoint}</dd></div>}
                </dl>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default SSOPage;
