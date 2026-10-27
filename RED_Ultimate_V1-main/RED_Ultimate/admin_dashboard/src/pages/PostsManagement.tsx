import { useCallback, useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import { Dialog } from '@/components/ui/dialog';
import { cn } from '@/utils/cn';
import { toast } from '@/hooks/useToast';
import {
  Search, RefreshCw, Trash2, FileText, Heart, MessageSquare, 
  Image as ImageIcon, Repeat, Undo, PieChart
} from 'lucide-react';
import {
  deleteAdminPost, getAdminPosts, getPostsOverview, restoreAdminPost,
  type AdminPost
} from '../api';
import { formatSafeDate } from './_shared';

const VISIBILITY_LABELS: Record<string, { label: string; className: string }> = {
  PUBLIC: { label: 'عام', className: 'bg-amber-500/20 text-amber-500 border-amber-500/30' },
  LOCAL_YEMEN: { label: 'محلي (اليمن)', className: 'bg-orange-500/20 text-orange-500 border-orange-500/30' },
};

export default function PostsManagement() {
  const [overview, setOverview] = useState<{ totalPosts: number; createdToday: number; deletedPosts: number; polls: number } | null>(null);
  const [posts, setPosts] = useState<AdminPost[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState('');
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  // Unified Dialog — بديل window.confirm الأصلية (نفس السلوك)
  const [deleteDialog, setDeleteDialog] = useState<{ open: boolean; post: AdminPost | null }>({ open: false, post: null });
  const [restoreDialog, setRestoreDialog] = useState<{ open: boolean; post: AdminPost | null }>({ open: false, post: null });

  const loadOverview = useCallback(async () => {
    try { setOverview(await getPostsOverview()); } catch { /* الإحصائيات ثانوية */ }
  }, []);

  const loadPosts = useCallback(async (p = page, s = pageSize, q = search, deleted = includeDeleted) => {
    setLoading(true);
    setLoadError('');
    try {
      const data = await getAdminPosts({ q: q || undefined, includeDeleted: deleted, page: p, size: s });
      const list = Array.isArray(data?.content) ? data.content : [];
      setPosts(list);
      setTotal(typeof data?.totalElements === 'number' ? data.totalElements : list.length);
    } catch (e: any) {
      setPosts([]);
      setTotal(0);
      setLoadError(e?.message || 'تعذّر تحميل المنشورات');
      toast.error('خطأ', e?.message || 'تعذّر تحميل المنشورات');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search, includeDeleted]);

  useEffect(() => { loadOverview(); }, [loadOverview]);
  useEffect(() => { loadPosts(); }, [loadPosts]);

  const onDelete = async (post: AdminPost) => {
    if (!post?.id) {
      toast.error('خطأ', 'معرّف المنشور مفقود');
      return;
    }
    setDeleteDialog({ open: true, post });
  };

  const confirmDelete = async () => {
    const post = deleteDialog.post;
    if (!post?.id) {
      toast.error('خطأ', 'معرّف المنشور مفقود');
      return;
    }
    setDeleteDialog({ open: false, post: null });
    try {
      await deleteAdminPost(post.id);
      toast.success('نجاح', 'حُذف المنشور');
      loadPosts();
      loadOverview();
    } catch (e: any) {
      toast.error('خطأ', e?.message || 'تعذّر حذف المنشور');
    }
  };

  const onRestore = async (post: AdminPost) => {
    if (!post?.id) {
      toast.error('خطأ', 'معرّف المنشور مفقود');
      return;
    }
    setRestoreDialog({ open: true, post });
  };

  const confirmRestore = async () => {
    const post = restoreDialog.post;
    if (!post?.id) {
      toast.error('خطأ', 'معرّف المنشور مفقود');
      return;
    }
    setRestoreDialog({ open: false, post: null });
    try {
      await restoreAdminPost(post.id);
      toast.success('نجاح', 'استُعيد المنشور');
      loadPosts();
      loadOverview();
    } catch (e: any) {
      toast.error('خطأ', e?.message || 'تعذّرت استعادة المنشور');
    }
  };

  const reactionsTotal = (p: AdminPost) =>
    Object.values(p?.reactionCounts ?? {}).reduce((sum, n) => sum + (typeof n === 'number' && Number.isFinite(n) ? n : 0), 0);

  const authorName = (p: AdminPost) => p?.authorDisplayName?.trim() || p?.authorUsername?.trim() || '—';

  return (
    <>
      <div className="p-6 space-y-6 bg-[#09090B] min-h-screen text-zinc-100" dir="rtl">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <Card className="bg-zinc-900 border-zinc-800 shadow-xl">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-zinc-400 flex items-center gap-2">
                <FileText className="h-4 w-4 text-emerald-400" />
                إجمالي المنشورات
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-zinc-100">{overview?.totalPosts ?? <Skeleton className="h-8 w-16 bg-zinc-800" />}</div>
            </CardContent>
          </Card>
          <Card className="bg-zinc-900 border-zinc-800 shadow-xl">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-zinc-400 flex items-center gap-2">
                <RefreshCw className="h-4 w-4 text-amber-500" />
                نُشر اليوم
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-amber-500">{overview?.createdToday ?? <Skeleton className="h-8 w-16 bg-zinc-800" />}</div>
            </CardContent>
          </Card>
          <Card className="bg-zinc-900 border-zinc-800 shadow-xl">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-zinc-400 flex items-center gap-2">
                <Trash2 className="h-4 w-4 text-red-400" />
                المحذوفة
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-red-400">{overview?.deletedPosts ?? <Skeleton className="h-8 w-16 bg-zinc-800" />}</div>
            </CardContent>
          </Card>
          <Card className="bg-zinc-900 border-zinc-800 shadow-xl">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-zinc-400 flex items-center gap-2">
                <PieChart className="h-4 w-4 text-purple-400" />
                الاستطلاعات
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-purple-400">{overview?.polls ?? <Skeleton className="h-8 w-16 bg-zinc-800" />}</div>
            </CardContent>
          </Card>
        </div>

        <Card className="bg-zinc-900 border-zinc-800 shadow-xl">
          <CardHeader className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 pb-4 border-b border-zinc-800">
            <div>
              <CardTitle className="text-lg font-bold text-emerald-400">إدارة المنشورات والتغريدات</CardTitle>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-2 px-3 py-1.5 bg-zinc-950 rounded-md border border-zinc-800">
                <span className="text-sm text-zinc-400">إظهار المحذوف</span>
                <Switch
                  checked={includeDeleted}
                  onCheckedChange={(v) => { setIncludeDeleted(v); setPage(0); loadPosts(0, pageSize, search, v); }}
                />
              </div>
              <div className="relative">
                <Search className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-zinc-500" />
                <Input
                  className="pl-3 pr-9 w-64 bg-zinc-950 border-zinc-800 text-zinc-100 focus:border-emerald-500/50"
                  placeholder="بحث بالنص أو الكاتب…"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      setPage(0); loadPosts(0, pageSize, search);
                    }
                  }}
                />
              </div>
              <Button 
                variant="outline" 
                className="bg-zinc-950 border-zinc-800 text-zinc-300 hover:text-emerald-400 hover:bg-zinc-900"
                onClick={() => { loadPosts(); loadOverview(); }}
              >
                <RefreshCw className={cn("h-4 w-4 ml-2", loading && "animate-spin")} />
                تحديث
              </Button>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-right">
                <thead className="bg-zinc-950 text-zinc-400 border-b border-zinc-800">
                  <tr>
                    <th className="px-4 py-3 font-medium">الكاتب</th>
                    <th className="px-4 py-3 font-medium">النص</th>
                    <th className="px-4 py-3 font-medium">الظهور</th>
                    <th className="px-4 py-3 font-medium">التفاعل</th>
                    <th className="px-4 py-3 font-medium">الحالة</th>
                    <th className="px-4 py-3 font-medium">نُشر</th>
                    <th className="px-4 py-3 font-medium">إجراءات</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-800/50">
                  {loading ? (
                    Array.from({ length: 5 }).map((_, i) => (
                      <tr key={i} className="hover:bg-zinc-900/50">
                        <td className="px-4 py-3"><Skeleton className="h-8 w-32 bg-zinc-800" /></td>
                        <td className="px-4 py-3"><Skeleton className="h-8 w-48 bg-zinc-800" /></td>
                        <td className="px-4 py-3"><Skeleton className="h-6 w-16 bg-zinc-800" /></td>
                        <td className="px-4 py-3"><Skeleton className="h-6 w-32 bg-zinc-800" /></td>
                        <td className="px-4 py-3"><Skeleton className="h-6 w-16 bg-zinc-800" /></td>
                        <td className="px-4 py-3"><Skeleton className="h-6 w-24 bg-zinc-800" /></td>
                        <td className="px-4 py-3"><Skeleton className="h-8 w-20 bg-zinc-800" /></td>
                      </tr>
                    ))
                  ) : posts.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-4 py-8 text-center text-zinc-500">
                        {loadError || 'لا توجد منشورات'}
                      </td>
                    </tr>
                  ) : (
                    posts.map((p) => {
                      const vis = (p.visibility && VISIBILITY_LABELS[p.visibility]) || { label: p.visibility || '—', className: 'bg-zinc-800 text-zinc-300' };
                      return (
                        <tr key={p.id ?? Math.random().toString()} className="hover:bg-zinc-900/50 transition-colors">
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-3">
                              <Avatar className="h-8 w-8 bg-zinc-800 border border-zinc-700">
                                <AvatarFallback className="text-zinc-400 bg-zinc-800">{authorName(p).charAt(0)}</AvatarFallback>
                              </Avatar>
                              <div>
                                <div className="font-medium text-zinc-200">{authorName(p)}</div>
                                <div className="text-xs text-zinc-500">@{p?.authorUsername?.trim() || '—'}</div>
                              </div>
                            </div>
                          </td>
                          <td className="px-4 py-3">
                            <div className="space-y-2">
                              <p className="line-clamp-2 text-zinc-300 max-w-md">
                                {p.text || <span className="text-zinc-500 italic">بلا نص</span>}
                              </p>
                              <div className="flex flex-wrap gap-2">
                                {p.kind === 'POLL' && (
                                  <Badge variant="outline" className="bg-purple-500/10 text-purple-400 border-purple-500/20">استطلاع</Badge>
                                )}
                                {(p.mediaCount ?? 0) > 0 && (
                                  <Badge variant="outline" className="bg-blue-500/10 text-blue-400 border-blue-500/20 flex items-center gap-1">
                                    <ImageIcon className="h-3 w-3" />
                                    {p.mediaCount} وسائط
                                  </Badge>
                                )}
                                {(p.hashtags ?? []).slice(0, 3).map((h) => (
                                  <Badge key={h} variant="secondary" className="bg-zinc-800 text-zinc-400 hover:bg-zinc-700">#{h}</Badge>
                                ))}
                              </div>
                            </div>
                          </td>
                          <td className="px-4 py-3">
                            <Badge variant="outline" className={cn("font-normal", vis.className)}>
                              {vis.label}
                            </Badge>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-4 text-zinc-400">
                              <span className="flex items-center gap-1" title="تفاعلات">
                                <Heart className="h-4 w-4" /> {reactionsTotal(p)}
                              </span>
                              <span className="flex items-center gap-1" title="ردود">
                                <MessageSquare className="h-4 w-4" /> {p.replyCount ?? '—'}
                              </span>
                              <span className="flex items-center gap-1" title="إعادات نشر">
                                <Repeat className="h-4 w-4" /> {p.repostCount ?? '—'}
                              </span>
                            </div>
                          </td>
                          <td className="px-4 py-3">
                            {p.deleted ? (
                              <Badge variant="outline" className="bg-red-500/10 text-red-500 border-red-500/20">محذوف</Badge>
                            ) : (
                              <Badge variant="outline" className="bg-emerald-500/10 text-emerald-500 border-emerald-500/20">منشور</Badge>
                            )}
                          </td>
                          <td className="px-4 py-3 text-zinc-400 whitespace-nowrap">
                            {formatSafeDate(p.createdAt)}
                          </td>
                          <td className="px-4 py-3">
                            {p.deleted ? (
                              <Button
                                size="sm"
                                variant="outline"
                                className="bg-amber-500/10 text-amber-500 border-amber-500/20 hover:bg-amber-500/20 hover:text-amber-400"
                                onClick={() => onRestore(p)}
                              >
                                <Undo className="h-4 w-4 ml-1" />
                                استعادة
                              </Button>
                            ) : (
                              <Button
                                size="sm"
                                variant="outline"
                                className="bg-red-500/10 text-red-500 border-red-500/20 hover:bg-red-500/20 hover:text-red-400"
                                onClick={() => onDelete(p)}
                                aria-label={`حذف منشور ${authorName(p)}`}
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
            
            <div className="p-4 border-t border-zinc-800 flex items-center justify-between text-sm text-zinc-400">
              <div>
                إجمالي {total} منشور
              </div>              <div className="flex items-center gap-2">
                <Button 
                  variant="outline" 
                  size="sm"
                  className="bg-zinc-950 border-zinc-800 text-zinc-300 hover:bg-zinc-900"
                  disabled={page === 0}
                  onClick={() => {
                    const newPage = page - 1;
                    setPage(newPage);
                    loadPosts(newPage);
                  }}
                >
                  السابق
                </Button>
                <span>صفحة {page + 1}</span>
                <Button 
                  variant="outline"
                  size="sm"
                  className="bg-zinc-950 border-zinc-800 text-zinc-300 hover:bg-zinc-900"
                  disabled={(page + 1) * pageSize >= total}
                  onClick={() => {
                    const newPage = page + 1;
                    setPage(newPage);
                    loadPosts(newPage);
                  }}
                >
                  التالي
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Confirm Dialogs — بديل window.confirm */}
        <Dialog
          open={deleteDialog.open}
          onOpenChange={(open) => { if (!open) setDeleteDialog({ open: false, post: null }); }}
          title="حذف المنشور؟ حذف ناعم — يبقى قابلًا للاستعادة."
        >
          <div className="flex items-center justify-end gap-2 py-2" dir="rtl">
            <Button variant="outline" onClick={() => setDeleteDialog({ open: false, post: null })}>إلغاء</Button>
            <Button variant="destructive" onClick={confirmDelete}>تأكيد الحذف</Button>
          </div>
        </Dialog>

        <Dialog
          open={restoreDialog.open}
          onOpenChange={(open) => { if (!open) setRestoreDialog({ open: false, post: null }); }}
          title="استعادة المنشور؟"
        >
          <div className="flex items-center justify-end gap-2 py-2" dir="rtl">
            <Button variant="outline" onClick={() => setRestoreDialog({ open: false, post: null })}>إلغاء</Button>
            <Button onClick={confirmRestore}>تأكيد الاستعادة</Button>
          </div>
        </Dialog>
      </div>
    </>
  );
}
