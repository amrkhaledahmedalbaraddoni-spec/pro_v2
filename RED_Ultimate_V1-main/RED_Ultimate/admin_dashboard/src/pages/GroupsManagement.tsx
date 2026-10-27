import React, { useCallback, useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Skeleton } from '@/components/ui/skeleton';
import { Search, RefreshCw, Trash2, Users, User, Crown, Eye } from 'lucide-react';
import { cn } from '@/utils/cn';
import { toast } from '@/hooks/useToast';
import { formatSafeDate } from './_shared';
import {
  deleteAdminGroup, getAdminGroupDetails, getAdminGroups, getGroupsOverview,
  removeGroupMember, type AdminGroup, type AdminGroupDetails
} from '../api';

const ROLE_LABELS: Record<string, { label: string; variant: "default" | "secondary" | "destructive" | "outline"; className?: string }> = {
  OWNER: { label: 'مالك', variant: 'outline', className: 'text-amber-500 border-amber-500/50 bg-amber-500/10' },
  ADMIN: { label: 'مشرف', variant: 'outline', className: 'text-blue-500 border-blue-500/50 bg-blue-500/10' },
  MEMBER: { label: 'عضو', variant: 'secondary' },
};

export default function GroupsManagement() {
  const [overview, setOverview] = useState<{ totalGroups: number; totalMembers: number; avgMembersPerGroup: number; createdToday: number } | null>(null);
  const [groups, setGroups] = useState<AdminGroup[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [details, setDetails] = useState<AdminGroupDetails | null>(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  // Unified Dialog — بديل window.confirm الأصلية (نفس السلوك)
  const [deleteDialog, setDeleteDialog] = useState<{ open: boolean; group: AdminGroup | null }>({ open: false, group: null });
  const [removeDialog, setRemoveDialog] = useState<{ open: boolean; userId: string; username: string }>({
    open: false, userId: '', username: '',
  });

  const loadOverview = useCallback(async () => {
    try { setOverview(await getGroupsOverview()); } catch { /* الإحصائيات ثانوية */ }
  }, []);

  const loadGroups = useCallback(async (p = page, s = pageSize, q = search) => {
    setLoading(true);
    try {
      const data = await getAdminGroups({ q: q || undefined, page: p, size: s });
      const list = Array.isArray(data?.content) ? data.content : [];
      setGroups(list);
      setTotal(typeof data?.totalElements === 'number' ? data.totalElements : list.length);
    } catch (e: any) {
      setGroups([]);
      setTotal(0);
      toast.error(e?.message || 'تعذّر تحميل المجموعات');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search]);

  useEffect(() => { loadOverview(); }, [loadOverview]);
  useEffect(() => { loadGroups(); }, [loadGroups]);

  const openDetails = async (group: AdminGroup) => {
    if (!group?.id) {
      toast.error('معرّف المجموعة مفقود');
      return;
    }
    setDrawerOpen(true);
    setDetailsLoading(true);
    try {
      setDetails(await getAdminGroupDetails(group.id));
    } catch (e: any) {
      toast.error(e?.message || 'تعذّر تحميل تفاصيل المجموعة');
      setDrawerOpen(false);
    } finally {
      setDetailsLoading(false);
    }
  };

  const onDeleteGroup = async (group: AdminGroup) => {
    if (!group?.id) {
      toast.error('معرّف المجموعة مفقود');
      return;
    }
    setDeleteDialog({ open: true, group });
  };

  const confirmDeleteGroup = async () => {
    const group = deleteDialog.group;
    if (!group?.id) {
      toast.error('معرّف المجموعة مفقود');
      return;
    }
    setDeleteDialog({ open: false, group: null });
    try {
      await deleteAdminGroup(group.id);
      toast.success(`حُذفت المجموعة «${group.name ?? '—'}»`);
      setDrawerOpen(false);
      loadGroups();
      loadOverview();
    } catch (e: any) {
      toast.error(e?.message || 'تعذّر حذف المجموعة');
    }
  };

  const onRemoveMember = async (userId: string, username: string) => {
    if (!details?.id || !userId) {
      toast.error('تعذّرت إزالة العضو: بيانات ناقصة');
      return;
    }
    setRemoveDialog({ open: true, userId, username });
  };

  const confirmRemoveMember = async () => {
    const { userId, username } = removeDialog;
    if (!details?.id || !userId) {
      toast.error('تعذّرت إزالة العضو: بيانات ناقصة');
      return;
    }
    setRemoveDialog({ open: false, userId: '', username: '' });
    try {
      await removeGroupMember(details.id, userId);
      toast.success(`أُزيل ${username} من المجموعة`);
      setDetails(await getAdminGroupDetails(details.id));
      loadGroups();
      loadOverview();
    } catch (e: any) {
      toast.error(e?.message || 'تعذّرت إزالة العضو');
    }
  };

  const members = Array.isArray(details?.members) ? details.members : [];

  return (
    <>
      <div className="space-y-6">
        {/* Overview Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card className="bg-card">
            <CardContent className="p-6">
              <div className="flex items-center space-x-4 space-x-reverse">
                <div className="p-3 bg-emerald-500/10 rounded-full">
                  <Users className="w-6 h-6 text-emerald-500" />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">إجمالي المجموعات</p>
                  <h3 className="text-2xl font-bold">{overview?.totalGroups ?? '—'}</h3>
                </div>
              </div>
            </CardContent>
          </Card>
          <Card className="bg-card">
            <CardContent className="p-6">
              <div className="flex items-center space-x-4 space-x-reverse">
                <div className="p-3 bg-blue-500/10 rounded-full">
                  <User className="w-6 h-6 text-blue-500" />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">إجمالي العضويات</p>
                  <h3 className="text-2xl font-bold">{overview?.totalMembers ?? '—'}</h3>
                </div>
              </div>
            </CardContent>
          </Card>
          <Card className="bg-card">
            <CardContent className="p-6">
              <div className="flex items-center space-x-4 space-x-reverse">
                <div className="p-3 bg-purple-500/10 rounded-full">
                  <Users className="w-6 h-6 text-purple-500" />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">متوسط الأعضاء</p>
                  <h3 className="text-2xl font-bold">
                    {typeof overview?.avgMembersPerGroup === 'number' ? overview.avgMembersPerGroup.toFixed(1) : '—'}
                  </h3>
                </div>
              </div>
            </CardContent>
          </Card>
          <Card className="bg-card">
            <CardContent className="p-6">
              <div className="flex items-center space-x-4 space-x-reverse">
                <div className="p-3 bg-amber-500/10 rounded-full">
                  <Crown className="w-6 h-6 text-amber-500" />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">أُنشئت اليوم</p>
                  <h3 className="text-2xl font-bold text-amber-500">{overview?.createdToday ?? '—'}</h3>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Groups Table */}
        <Card className="bg-card">
          <CardHeader className="flex flex-col sm:flex-row items-center justify-between space-y-4 sm:space-y-0 pb-4">
            <CardTitle className="text-xl font-bold">إدارة المجموعات</CardTitle>
            <div className="flex items-center space-x-2 space-x-reverse">
              <div className="relative">
                <Search className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                <Input
                  className="pl-3 pr-9 w-full sm:w-64"
                  placeholder="بحث باسم المجموعة…"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      setPage(0);
                      loadGroups(0, pageSize, search);
                    }
                  }}
                />
              </div>
              <Button variant="outline" size="icon" onClick={() => { loadGroups(); loadOverview(); }}>
                <RefreshCw className="w-4 h-4" />
              </Button>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left rtl:text-right text-muted-foreground">
                <thead className="text-xs text-muted-foreground uppercase bg-muted/50">
                  <tr>
                    <th className="px-6 py-3 font-medium">المجموعة</th>
                    <th className="px-6 py-3 font-medium">المالك</th>
                    <th className="px-6 py-3 font-medium">الأعضاء</th>
                    <th className="px-6 py-3 font-medium">أُنشئت</th>
                    <th className="px-6 py-3 font-medium">إجراءات</th>
                  </tr>
                </thead>
                <tbody>
                  {loading ? (
                    Array.from({ length: 5 }).map((_, i) => (
                      <tr key={i} className="border-b border-border/50">
                        <td className="px-6 py-4"><Skeleton className="h-10 w-32" /></td>
                        <td className="px-6 py-4"><Skeleton className="h-6 w-20" /></td>
                        <td className="px-6 py-4"><Skeleton className="h-6 w-12" /></td>
                        <td className="px-6 py-4"><Skeleton className="h-6 w-24" /></td>
                        <td className="px-6 py-4"><Skeleton className="h-8 w-24" /></td>
                      </tr>
                    ))
                  ) : groups.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-6 py-8 text-center text-muted-foreground">
                        لا توجد مجموعات
                      </td>
                    </tr>
                  ) : (
                    groups.map((g) => (
                      <tr key={g.id || Math.random()} className="bg-card hover:bg-muted/30 border-b border-border/50 transition-colors">
                        <td className="px-6 py-4 whitespace-nowrap">
                          <div className="flex items-center space-x-3 space-x-reverse">
                            <Avatar className="w-10 h-10 border border-border/50 bg-muted">
                              <AvatarImage src={g.avatarUrl || undefined} />
                              <AvatarFallback><Users className="w-5 h-5 text-muted-foreground" /></AvatarFallback>
                            </Avatar>
                            <div>
                              <div className="font-semibold text-foreground">{g.name?.trim() || '—'}</div>
                              {g.description && (
                                <div className="text-xs text-muted-foreground mt-0.5 truncate max-w-[200px]">
                                  {g.description}
                                </div>
                              )}
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <code className="text-xs px-1.5 py-0.5 bg-muted rounded font-mono text-muted-foreground">
                            {g.ownerRedId?.trim() || '—'}
                          </code>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <Badge variant="outline" className="bg-cyan-500/10 text-cyan-500 border-cyan-500/20">
                            <User className="w-3 h-3 ml-1" />
                            {g.memberCount ?? '—'}
                          </Badge>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm">
                          {formatSafeDate(g.createdAt)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <div className="flex items-center space-x-2 space-x-reverse">
                            <Button variant="outline" size="sm" onClick={() => openDetails(g)}>
                              <Eye className="w-4 h-4 ml-1.5" />
                              التفاصيل
                            </Button>
                            <Button variant="destructive" size="icon" className="h-8 w-8" onClick={() => onDeleteGroup(g)}>
                              <Trash2 className="w-4 h-4" />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            {/* Simple Pagination Controls */}
            {!loading && total > 0 && (
              <div className="flex items-center justify-between px-6 py-4 border-t border-border/50 text-sm text-muted-foreground">
                <div>
                  إجمالي {total} مجموعة
                </div>
                <div className="flex space-x-2 space-x-reverse">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page === 0}
                    onClick={() => {
                      const newPage = page - 1;
                      setPage(newPage);
                      loadGroups(newPage, pageSize);
                    }}
                  >
                    السابق
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={(page + 1) * pageSize >= total}
                    onClick={() => {
                      const newPage = page + 1;
                      setPage(newPage);
                      loadGroups(newPage, pageSize);
                    }}
                  >
                    التالي
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Confirm Dialogs — بديل window.confirm */}
        <Dialog
          open={deleteDialog.open}
          onOpenChange={(open) => { if (!open) setDeleteDialog({ open: false, group: null }); }}
          title={`حذف «${deleteDialog.group?.name ?? '—'}» نهائيًا؟ تُحذف المجموعة وكل عضوياتها.`}
        >
          <div className="flex items-center justify-end gap-2 py-2" dir="rtl">
            <Button variant="outline" onClick={() => setDeleteDialog({ open: false, group: null })}>إلغاء</Button>
            <Button variant="destructive" onClick={confirmDeleteGroup}>تأكيد الحذف</Button>
          </div>
        </Dialog>

        <Dialog
          open={removeDialog.open}
          onOpenChange={(open) => { if (!open) setRemoveDialog({ open: false, userId: '', username: '' }); }}
          title={`إزالة ${removeDialog.username || '—'} من المجموعة؟`}
        >
          <div className="flex items-center justify-end gap-2 py-2" dir="rtl">
            <Button variant="outline" onClick={() => setRemoveDialog({ open: false, userId: '', username: '' })}>إلغاء</Button>
            <Button variant="destructive" onClick={confirmRemoveMember}>تأكيد الإزالة</Button>
          </div>
        </Dialog>

        {/* Details Dialog */}
        <Dialog open={drawerOpen} onOpenChange={setDrawerOpen}>
          <DialogContent className="sm:max-w-xl max-h-[85vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle className="flex items-center space-x-3 space-x-reverse text-xl">
                {details ? (
                  <>
                    <Avatar className="w-8 h-8 border border-border/50">
                      <AvatarImage src={details.avatarUrl || undefined} />
                      <AvatarFallback><Users className="w-4 h-4" /></AvatarFallback>
                    </Avatar>
                    <span>{details.name?.trim() || '—'}</span>
                  </>
                ) : 'تفاصيل المجموعة'}
              </DialogTitle>
            </DialogHeader>

            {detailsLoading ? (
              <div className="space-y-4 py-4">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-32 w-full mt-4" />
              </div>
            ) : details ? (
              <div className="space-y-6 mt-4">
                {details.description && (
                  <p className="text-sm text-muted-foreground">{details.description}</p>
                )}
                
                <div className="text-sm flex flex-col space-y-2">
                  <div className="flex items-center">
                    <span className="text-muted-foreground w-16">المالك:</span>
                    <code className="text-xs px-1.5 py-0.5 bg-muted rounded font-mono text-muted-foreground">
                      {details.ownerRedId?.trim() || '—'}
                    </code>
                  </div>
                  <div className="flex items-center">
                    <span className="text-muted-foreground w-16">أُنشئت:</span>
                    <span>{formatSafeDate(details.createdAt)}</span>
                  </div>
                </div>

                <div className="space-y-3">
                  <h4 className="font-bold text-foreground">الأعضاء ({members.length})</h4>
                  
                  {members.length === 0 ? (
                    <div className="text-center py-6 text-sm text-muted-foreground bg-muted/20 rounded-lg">
                      لا يوجد أعضاء
                    </div>
                  ) : (
                    <div className="border border-border/50 rounded-lg overflow-hidden">
                      <table className="w-full text-sm text-left rtl:text-right">
                        <thead className="bg-muted/50 text-xs text-muted-foreground">
                          <tr>
                            <th className="px-4 py-2 font-medium">العضو</th>
                            <th className="px-4 py-2 font-medium">الدور</th>
                            <th className="px-4 py-2 font-medium">انضم</th>
                            <th className="px-4 py-2 font-medium"></th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-border/50">
                          {members.slice(0, 50).map((m) => {
                            const roleConfig = (m.role && ROLE_LABELS[m.role]) || { label: m.role || '—', variant: 'secondary' };
                            
                            return (
                              <tr key={m.userId || m.redId || Math.random()} className="bg-card hover:bg-muted/20">
                                <td className="px-4 py-2 whitespace-nowrap">
                                  <div className="flex items-center space-x-2 space-x-reverse">
                                    <Avatar className="w-6 h-6">
                                      <AvatarFallback className="text-[10px]"><User className="w-3 h-3" /></AvatarFallback>
                                    </Avatar>
                                    <div>
                                      <div className="font-medium text-xs">{m.username?.trim() || '—'}</div>
                                      <code className="text-[10px] text-muted-foreground font-mono">{m.redId?.trim() || '—'}</code>
                                    </div>
                                  </div>
                                </td>
                                <td className="px-4 py-2 whitespace-nowrap">
                                  <Badge 
                                    variant={roleConfig.variant as any} 
                                    className={cn("text-[10px] px-1.5 py-0", roleConfig.className)}
                                  >
                                    {m.role === 'OWNER' && <Crown className="w-3 h-3 ml-1" />}
                                    {roleConfig.label}
                                  </Badge>
                                </td>
                                <td className="px-4 py-2 whitespace-nowrap text-xs text-muted-foreground">
                                  {formatSafeDate(m.joinedAt)}
                                </td>
                                <td className="px-4 py-2 whitespace-nowrap text-left">
                                  {m.role !== 'OWNER' && m.userId && (
                                    <Button
                                      variant="ghost"
                                      size="icon"
                                      className="h-6 w-6 text-destructive hover:bg-destructive/10 hover:text-destructive"
                                      onClick={() => onRemoveMember(m.userId, m.username ?? '—')}
                                    >
                                      <Trash2 className="w-3.5 h-3.5" />
                                    </Button>
                                  )}
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                      {members.length > 50 && (
                        <div className="text-xs text-center py-2 text-muted-foreground bg-muted/20">
                          يتم عرض أول 50 عضواً فقط
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>
            ) : null}
          </DialogContent>
        </Dialog>
      </div>
    </>
  );
}
