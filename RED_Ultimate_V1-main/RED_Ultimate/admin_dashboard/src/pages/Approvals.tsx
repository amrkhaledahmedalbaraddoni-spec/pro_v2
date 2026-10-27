import { useState, useEffect } from 'react';
import { Table, Button, Tag, Space, message, Modal, Input, Typography, Card, Descriptions, Alert } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CheckOutlined, CloseOutlined, SafetyCertificateOutlined, ReloadOutlined } from '@ant-design/icons';
import { getPendingApprovals, approveRejectUser } from '../api';
import { Dialog } from '@/components/ui/dialog';
import { formatSafeDate, formatSafeTime } from './_shared';

interface DeviceInfo {
  id: string;
  deviceName?: string;
  name?: string;
  platform?: string;
  status?: string;
  identityFingerprint?: string;
}

interface PendingUser {
  id: string;
  redId: string;
  displayName?: string;
  name?: string;
  username: string;
  createdAt?: string;
  devices?: DeviceInfo[];
  status?: string;
}

/**
 * سلطة اعتماد حسابات يونس — الموافقات المعلقة
 * بيانات حقيقية 100% مربوطة بـ /api/admin/users/pending
 */
export default function Approvals() {
  const [pendingUsers, setPendingUsers] = useState<PendingUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [rejecting, setRejecting] = useState<PendingUser | null>(null);
  const [approving, setApproving] = useState<PendingUser | null>(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getPendingApprovals();
      setPendingUsers(Array.isArray(data) ? data : []);
    } catch (e: any) {
      setPendingUsers([]);
      setError(e?.message || 'تعذر تحميل طلبات الموافقة');
      message.error(e?.message || 'تعذر تحميل طلبات الموافقة');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const handleAction = async (user: PendingUser, status: 'APPROVED' | 'REJECTED', rejectionReason?: string) => {
    try {
      await approveRejectUser(user.id, status, rejectionReason);
      message.success(
        status === 'APPROVED'
          ? `تمت الموافقة على ${user.displayName || user.username} وإصدار شهادات ${user.devices?.length || 1} جهاز`
          : 'تم رفض الحساب'
      );
      setRejecting(null);
      setReason('');
      await load();
    } catch (e: any) {
      message.error(e?.message || 'فشل الإجراء');
    }
  };

  const columns: ColumnsType<PendingUser> = [
    {
      title: 'معرّف يونس',
      dataIndex: 'redId',
      width: 160,
      render: (v: string) => <Typography.Text copyable>{v}</Typography.Text>,
    },
    {
      title: 'المستخدم',
      key: 'user',
      render: (_, u) => (
        <>
          <b>{u.displayName || u.name || u.username}</b>
          <br />
          <span style={{ color: '#888' }}>@{u.username}</span>
        </>
      ),
    },
    {
      title: 'التسجيل',
      dataIndex: 'createdAt',
      render: (v: string) => (v ? `${formatSafeDate(v, 'ar')} ${formatSafeTime(v, 'ar')}` : '—'),
    },
    {
      title: 'الأجهزة',
      dataIndex: 'devices',
      render: (devices: DeviceInfo[] | undefined) =>
        devices && devices.length > 0 ? (
          <Space direction="vertical">
            {devices.map((d) => (
              <Tag key={d.id} icon={<SafetyCertificateOutlined />} color="gold">
                {d.deviceName || d.name || 'جهاز'} · {d.platform || '—'} · {d.status || 'PENDING'}
              </Tag>
            ))}
          </Space>
        ) : (
          <Tag>جهاز واحد</Tag>
        ),
    },
    {
      title: 'الحالة',
      key: 'status',
      render: () => <Tag color="orange">PENDING</Tag>,
    },
    {
      title: 'إجراء',
      key: 'action',
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button
            type="primary"
            icon={<CheckOutlined />}
            onClick={() => setApproving(record)}
          >
            موافقة
          </Button>
          <Button
            danger
            icon={<CloseOutlined />}
            onClick={() => {
              setRejecting(record);
              setReason('');
            }}
          >
            رفض
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
    <Card
      title="سلطة اعتماد حسابات يونس — الموافقات المعلقة"
      extra={
        <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
          تحديث
        </Button>
      }
    >
      <Typography.Paragraph type="secondary">
        يعرض البصمة الحقيقية لمفتاح الهوية لكل جهاز — لا توافق قبل التحقق من البصمة عبر قناة موثوقة.
      </Typography.Paragraph>
      {error && (
        <Alert type="error" showIcon message="تعذر تحميل طلبات الموافقة" description={error} style={{ marginBottom: 16 }}
          action={<Button size="small" onClick={load}>إعادة المحاولة</Button>} />
      )}
      <Table
        dataSource={pendingUsers}
        columns={columns}
        rowKey={(r: PendingUser) => r?.id ?? `${r?.redId ?? 'x'}-${r?.username ?? ''}`}
        loading={loading}
        scroll={{ x: 1050 }}
        expandable={
          pendingUsers.some((u) => u.devices && u.devices.length > 0)
            ? {
                expandedRowRender: (u) =>
                  u.devices && u.devices.length > 0 ? (
                    <Descriptions bordered size="small" column={1}>
                      {u.devices.map((d) => (
                        <Descriptions.Item key={d.id} label={`${d.deviceName || d.name} — بصمة مفتاح الهوية`}>
                          <Typography.Text copyable code>
                            {d.identityFingerprint || '—'}
                          </Typography.Text>
                        </Descriptions.Item>
                      ))}
                    </Descriptions>
                  ) : null,
              }
            : undefined
        }
        locale={{ emptyText: 'لا توجد طلبات معلقة — كل الحسابات معالجة' }}
      />
      <Modal
        title={`رفض حساب ${rejecting?.redId || ''}`}
        open={Boolean(rejecting)}
        okText="تأكيد الرفض"
        cancelText="إلغاء"
        okButtonProps={{ danger: true }}
        onCancel={() => setRejecting(null)}
        onOk={() => rejecting && handleAction(rejecting, 'REJECTED', reason)}
      >
        <Input.TextArea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={4}
          placeholder="سبب الرفض (يظهر لصاحب الحساب — اختياري لكن يفضل توضيحه)"
        />
      </Modal>
      {/* Approve Dialog — الموحد بديل Modal.confirm (نفس السلوك) */}
      <Dialog
        open={approving !== null}
        onOpenChange={(open) => { if (!open) setApproving(null); }}
        title={`الموافقة على ${approving?.displayName || approving?.username || ''}`}
      >
        <div className="space-y-4 py-2" dir="rtl">
          <p className="text-sm text-muted-foreground">
            {approving ? `سيتم اعتماد ${approving.redId} وتوقيع ${approving.devices?.length || 1} جهاز بمفتاح سلطة يونس.` : ''}
          </p>
          <div className="flex items-center justify-end gap-2">
            <Button onClick={() => setApproving(null)}>إلغاء</Button>
            <Button
              type="primary"
              onClick={() => {
                if (approving) {
                  const u = approving;
                  setApproving(null);
                  void handleAction(u, 'APPROVED');
                }
              }}
            >
              موافقة وتوقيع
            </Button>
          </div>
        </div>
      </Dialog>
    </Card>
    </>
  );
}
