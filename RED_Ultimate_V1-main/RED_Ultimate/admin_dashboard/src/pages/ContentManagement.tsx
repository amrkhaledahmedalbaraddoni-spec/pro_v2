import React, { useEffect, useState } from 'react';
import {
  BarChart2, Calendar, Tags, Image as ImageIcon, Plus, RefreshCw, Check, X,
  Trash2, TrendingUp, Flame, Ban, CheckCircle, Search, MapPin, StopCircle,
  Eye, EyeOff, CircleDashed
} from 'lucide-react';
import {
  getPolls, createPoll, closePoll, deletePoll,
  getEvents, createEvent, cancelEvent, deleteEvent,
  getTrendingHashtags, getPopularHashtags, blockHashtag, unblockHashtag,
  getStickerPacks, createStickerPack, publishStickerPack, deleteStickerPack,
  getAdminStories, getAdminStoriesOverview, hideAdminStory, unhideAdminStory, deleteAdminStory,
  type Poll, type Event, type Hashtag, type StickerPack, type AdminStory
} from '../api';
import { formatSafeDate, formatSafeTime, LoadingState, ErrorState, EmptyState } from './_shared';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Switch } from '@/components/ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { Skeleton } from '@/components/ui/skeleton';
import { toast } from '@/hooks/useToast';
import { cn } from '@/utils/cn';
import { useTranslation } from 'react-i18next';

const POLL_TYPE_LABELS: Record<string, { label: string; color: string }> = {
  SINGLE_CHOICE: { label: 'ط§ط®طھظٹط§ط± ظˆط§ط­ط¯', color: 'bg-blue-500/20 text-blue-400 border-blue-500/30' },
  MULTIPLE_CHOICE: { label: 'ط§ط®طھظٹط§ط± ظ…طھط¹ط¯ط¯', color: 'bg-purple-500/20 text-purple-400 border-purple-500/30' },
  RANKED: { label: 'طھطµظ†ظٹظپ', color: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30' },
};

const POLL_STATUS_LABELS: Record<string, { label: string; color: string }> = {
  DRAFT: { label: 'ظ…ط³ظˆط¯ط©', color: 'bg-gray-500/20 text-gray-400 border-gray-500/30' },
  ACTIVE: { label: 'ظ†ط´ط·ط©', color: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30' },
  CLOSED: { label: 'ظ…ط؛ظ„ظ‚ط©', color: 'bg-orange-500/20 text-orange-400 border-orange-500/30' },
  ARCHIVED: { label: 'ظ…ط¤ط±ط´ظپط©', color: 'bg-zinc-500/20 text-zinc-400 border-zinc-500/30' },
};

const EVENT_TYPE_LABELS: Record<string, { label: string; color: string }> = {
  MEETING: { label: 'ط§ط¬طھظ…ط§ط¹', color: 'bg-blue-500/20 text-blue-400 border-blue-500/30' },
  CONFERENCE: { label: 'ظ…ط¤طھظ…ط±', color: 'bg-purple-500/20 text-purple-400 border-purple-500/30' },
  WEBINAR: { label: 'ظ†ط¯ظˆط©', color: 'bg-cyan-500/20 text-cyan-400 border-cyan-500/30' },
  SOCIAL: { label: 'ط§ط¬طھظ…ط§ط¹ظٹ', color: 'bg-pink-500/20 text-pink-400 border-pink-500/30' },
  CELEBRATION: { label: 'ط§ط­طھظپط§ظ„', color: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30' },
  OTHER: { label: 'ط£ط®ط±ظ‰', color: 'bg-gray-500/20 text-gray-400 border-gray-500/30' },
};

const EVENT_STATUS_LABELS: Record<string, { label: string; color: string }> = {
  DRAFT: { label: 'ظ…ط³ظˆط¯ط©', color: 'bg-gray-500/20 text-gray-400 border-gray-500/30' },
  SCHEDULED: { label: 'ظ…ط¬ط¯ظˆظ„', color: 'bg-blue-500/20 text-blue-400 border-blue-500/30' },
  LIVE: { label: 'ظ…ط¨ط§ط´ط±', color: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30' },
  ENDED: { label: 'ظ…ظ†طھظ‡ظٹ', color: 'bg-gray-500/20 text-gray-400 border-gray-500/30' },
  CANCELLED: { label: 'ظ…ظ„ط؛ظٹ', color: 'bg-red-500/20 text-red-400 border-red-500/30' },
};

const EVENT_VISIBILITY_LABELS: Record<string, string> = {
  PUBLIC: 'ط¹ط§ظ…',
  PRIVATE: 'ط®ط§طµ',
  INVITATION_ONLY: 'ط¨ط¯ط¹ظˆط© ظپظ‚ط·',
};

const textareaClasses = "flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:border-emerald-500 disabled:cursor-not-allowed disabled:opacity-50 transition-colors";

export default function ContentManagement() {
  const { t } = useTranslation();

  return (
    <>
      <div className="w-full space-y-6 text-right" dir="rtl">
        <div>
          <h2 className="text-3xl font-bold tracking-tight text-emerald-400 flex items-center gap-2">
            <BarChart2 className="w-8 h-8" />
            ط¥ط¯ط§ط±ط© ط§ظ„ظ…ط­طھظˆظ‰
          </h2>
          <p className="text-muted-foreground mt-2">
            ط¥ط¯ط§ط±ط© ط§ظ„ط§ط³طھط·ظ„ط§ط¹ط§طھطŒ ط§ظ„ط£ط­ط¯ط§ط«طŒ ط§ظ„ظ‡ط§ط´طھط§ط¬ط§طھطŒ ط§ظ„ظ…ظ„طµظ‚ط§طھطŒ ظˆط§ظ„ط­ط§ظ„ط§طھ (ط§ظ„ظ‚طµطµ)
          </p>
        </div>

        <Tabs defaultValue="polls" className="w-full">
          <TabsList className="mb-4 bg-zinc-900 border border-zinc-800 p-1">
            <TabsTrigger value="stories" className="data-[state=active]:bg-emerald-500/10 data-[state=active]:text-emerald-400">
              <CircleDashed className="w-4 h-4 ml-2" /> ط§ظ„ط­ط§ظ„ط§طھ
            </TabsTrigger>
            <TabsTrigger value="polls" className="data-[state=active]:bg-emerald-500/10 data-[state=active]:text-emerald-400">
              <BarChart2 className="w-4 h-4 ml-2" /> ط§ظ„ط§ط³طھط·ظ„ط§ط¹ط§طھ
            </TabsTrigger>
            <TabsTrigger value="events" className="data-[state=active]:bg-emerald-500/10 data-[state=active]:text-emerald-400">
              <Calendar className="w-4 h-4 ml-2" /> ط§ظ„ط£ط­ط¯ط§ط«
            </TabsTrigger>
            <TabsTrigger value="hashtags" className="data-[state=active]:bg-emerald-500/10 data-[state=active]:text-emerald-400">
              <Tags className="w-4 h-4 ml-2" /> ط§ظ„ظ‡ط§ط´طھط§ط¬ط§طھ
            </TabsTrigger>
            <TabsTrigger value="stickers" className="data-[state=active]:bg-emerald-500/10 data-[state=active]:text-emerald-400">
              <ImageIcon className="w-4 h-4 ml-2" /> ط§ظ„ظ…ظ„طµظ‚ط§طھ
            </TabsTrigger>
          </TabsList>
          
          <TabsContent value="stories" className="mt-0"><StoriesTab /></TabsContent>
          <TabsContent value="polls" className="mt-0"><PollsTab /></TabsContent>
          <TabsContent value="events" className="mt-0"><EventsTab /></TabsContent>
          <TabsContent value="hashtags" className="mt-0"><HashtagsTab /></TabsContent>
          <TabsContent value="stickers" className="mt-0"><StickersTab /></TabsContent>
        </Tabs>
      </div>
    </>
  );
}

// â”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پ Stories Tab (ط§ظ„ط­ط§ظ„ط§طھ) â”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پ
const STORY_TYPE_LABELS: Record<string, string> = {
  TEXT: 'ظ†طµ',
  VOICE: 'طµظˆطھ',
  IMAGE: 'طµظˆط±ط©',
  VIDEO: 'ظپظٹط¯ظٹظˆ',
};
const STORY_VISIBILITY_LABELS: Record<string, string> = {
  CONTACTS: 'ط¬ظ‡ط§طھ ط§ظ„ط§طھطµط§ظ„',
  EVERYONE: 'ط§ظ„ط¬ظ…ظٹط¹',
  SELECTED: 'ظ…ط­ط¯ط¯',
};

function StoriesTab() {
  const [stories, setStories] = useState<AdminStory[]>([]);
  const [overview, setOverview] = useState<{ activeStories: number; hiddenStories: number; viewsLast24h: number } | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(20);
  const [owner, setOwner] = useState('');
  const [showHidden, setShowHidden] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [confirmDelete, setConfirmDelete] = useState<AdminStory | null>(null);

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const [ov, list] = await Promise.all([
        getAdminStoriesOverview(),
        getAdminStories({ owner: owner || undefined, includeHidden: showHidden, page, size: pageSize }),
      ]);
      setOverview(ov);
      setStories(list.content);
      setTotal(list.totalElements);
    } catch {
      setError('طھط¹ط°ط± طھط­ظ…ظٹظ„ ط§ظ„ط­ط§ظ„ط§طھ â€” ط§ظ„ط®ط§ط¯ظ… ط؛ظٹط± ظ…طھطµظ„ ط£ظˆ ط§ظ„طµظ„ط§ط­ظٹط© ط؛ظٹط± ظƒط§ظپظٹط©');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); /* eslint-disable-line react-hooks/exhaustive-deps */ }, [page, showHidden]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="space-y-4">
      {/* ط¥ط­طµط§ط¦ظٹط§طھ ط¹ط§ظ…ط© */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card><CardContent className="pt-6">
          <p className="text-sm text-muted-foreground">ط­ط§ظ„ط§طھ ظ†ط´ط·ط©</p>
          <p className="text-2xl font-bold text-emerald-400">{overview?.activeStories ?? 'â€”'}</p>
        </CardContent></Card>
        <Card><CardContent className="pt-6">
          <p className="text-sm text-muted-foreground">ط­ط§ظ„ط§طھ ظ…ط®ظپظٹط© (ط¥ط´ط±ط§ظپ)</p>
          <p className="text-2xl font-bold text-amber-400">{overview?.hiddenStories ?? 'â€”'}</p>
        </CardContent></Card>
        <Card><CardContent className="pt-6">
          <p className="text-sm text-muted-foreground">ظ…ط´ط§ظ‡ط¯ط§طھ ط¢ط®ط± 24 ط³ط§ط¹ط©</p>
          <p className="text-2xl font-bold text-blue-400">{overview?.viewsLast24h ?? 'â€”'}</p>
        </CardContent></Card>
      </div>

      <div className="flex flex-col sm:flex-row gap-2 sm:items-center">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            value={owner}
            onChange={(e) => setOwner(e.target.value)}
            placeholder="ط¨ط­ط« ط¨ظ…ط¹ط±ظپ ط§ظ„ظ…ط§ظ„ظƒ ط£ظˆ RedIDâ€¦"
            className="pr-9 bg-zinc-900 border-zinc-800"
            onKeyDown={(e) => { if (e.key === 'Enter') { setPage(0); void load(); } }}
          />
        </div>
        <label className="flex items-center gap-2 text-sm text-muted-foreground cursor-pointer">
          <input type="checkbox" checked={showHidden} onChange={(e) => { setShowHidden(e.target.checked); setPage(0); }} className="accent-emerald-500" />
          ط¥ط¸ظ‡ط§ط± ط§ظ„ظ…ط®ظپظٹط©
        </label>
        <Button variant="outline" size="sm" onClick={() => void load()} disabled={loading} className="border-zinc-800">
          <RefreshCw className={cn('h-4 w-4 ml-2', loading && 'animate-spin')} /> طھط­ط¯ظٹط«
        </Button>
      </div>

      {error && <ErrorState message={error} onRetry={() => void load()} />}

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">ط§ظ„ط­ط§ظ„ط§طھ ط§ظ„ظ†ط´ط·ط© ({total})</CardTitle>
          <CardDescription>ط­ط§ظ„ط§طھ ط§ظ„ظ…ط³طھط®ط¯ظ…ظٹظ† ط§ظ„طھظٹ ظ„ظ… طھظ†طھظ‡ظگ طµظ„ط§ط­ظٹطھظ‡ط§ ط¨ط¹ط¯ (24 ط³ط§ط¹ط©) â€” ط§ظ„ط¥ط®ظپط§ط، ظ‚ط§ط¨ظ„ ظ„ظ„طھط±ط§ط¬ط¹طŒ ظˆط§ظ„ط­ط°ظپ ظ†ظ‡ط§ط¦ظٹ.</CardDescription>
        </CardHeader>
        <CardContent>
          {loading && stories.length === 0 ? (
            <Skeleton className="h-40 w-full bg-zinc-900" />
          ) : stories.length === 0 && !error ? (
            <EmptyState icon={<CircleDashed className="h-10 w-10 text-foreground/60" aria-hidden="true" />} title="ظ„ط§ ط­ط§ظ„ط§طھ" description="ظ„ط§ طھظˆط¬ط¯ ط­ط§ظ„ط§طھ ظ…ط·ط§ط¨ظ‚ط© ط­ط§ظ„ظٹظ‹ط§" />
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="border-zinc-800">
                  <TableHead>ط§ظ„ظ…ط§ظ„ظƒ</TableHead>
                  <TableHead>ط§ظ„ظ†ظˆط¹</TableHead>
                  <TableHead>ط§ظ„ظ†طµ ط§ظ„طھظˆط¶ظٹط­ظٹ</TableHead>
                  <TableHead>ط§ظ„ط¬ظ…ظ‡ظˆط±</TableHead>
                  <TableHead>ط§ظ„ظ…ط´ط§ظ‡ط¯ط§طھ</TableHead>
                  <TableHead>طھظ†طھظ‡ظٹ ظپظٹ</TableHead>
                  <TableHead>ط§ظ„ط­ط§ظ„ط©</TableHead>
                  <TableHead className="text-left">ط¥ط¬ط±ط§ط،ط§طھ</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {stories.map((s) => (
                  <TableRow key={s.id} className="border-zinc-800">
                    <TableCell>
                      <p className="text-zinc-200 font-medium">{s.ownerDisplayName}</p>
                      <p className="text-xs text-zinc-500" dir="ltr">@{s.ownerUsername} آ· {s.ownerRedId}</p>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline" className="bg-zinc-900 border-zinc-700 text-zinc-300">
                        {STORY_TYPE_LABELS[s.mediaType] || s.mediaType}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-[220px] truncate text-zinc-300">{s.caption || 'â€”'}</TableCell>
                    <TableCell className="text-zinc-400">{STORY_VISIBILITY_LABELS[s.visibility] || s.visibility}</TableCell>
                    <TableCell className="text-zinc-300 font-mono">{s.viewCount}</TableCell>
                    <TableCell className="text-zinc-400 text-xs">{formatSafeDate(s.expiresAt)}</TableCell>
                    <TableCell>
                      {s.hidden
                        ? <Badge variant="outline" className="bg-amber-500/10 border-amber-500/30 text-amber-400">ظ…ط®ظپظٹط©</Badge>
                        : <Badge variant="outline" className="bg-emerald-500/10 border-emerald-500/30 text-emerald-400">ط¸ط§ظ‡ط±ط©</Badge>}
                    </TableCell>
                    <TableCell className="text-left">
                      <div className="flex justify-end gap-1">
                        {s.hidden ? (
                          <TooltipProvider>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button variant="ghost" size="icon" className="h-8 w-8 text-emerald-400" onClick={async () => { await unhideAdminStory(s.id); void load(); }}>
                                  <Eye className="h-4 w-4" />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>ط¥ط¸ظ‡ط§ط± ظ…ط¬ط¯ط¯ظ‹ط§</TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        ) : (
                          <TooltipProvider>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button variant="ghost" size="icon" className="h-8 w-8 text-amber-400" onClick={async () => { await hideAdminStory(s.id); void load(); }}>
                                  <EyeOff className="h-4 w-4" />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>ط¥ط®ظپط§ط، (ط¥ط´ط±ط§ظپ)</TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        )}
                        <TooltipProvider>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <Button variant="ghost" size="icon" className="h-8 w-8 text-red-400" onClick={() => setConfirmDelete(s)}>
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </TooltipTrigger>
                            <TooltipContent>ط­ط°ظپ ظ†ظ‡ط§ط¦ظٹ</TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          {totalPages > 1 && (
            <div className="flex items-center justify-between mt-4">
              <p className="text-xs text-muted-foreground">طµظپط­ط© {page + 1} ظ…ظ† {totalPages}</p>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="border-zinc-800">ط§ظ„ط³ط§ط¨ظ‚</Button>
                <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)} className="border-zinc-800">ط§ظ„طھط§ظ„ظٹ</Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={!!confirmDelete} onOpenChange={(open) => !open && setConfirmDelete(null)}>
        <DialogContent className="bg-zinc-950 border-zinc-800">
          <DialogHeader>
            <DialogTitle className="text-red-400">ط­ط°ظپ ط§ظ„ط­ط§ظ„ط© ظ†ظ‡ط§ط¦ظٹظ‹ط§طں</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-zinc-300">
            ط³ظٹظڈط­ط°ظپ ظ…ط­طھظˆظ‰ ط§ظ„ط­ط§ظ„ط© ظˆظˆط³ط§ط¦ط·ظ‡ط§ ظˆظ…ط´ط§ظ‡ط¯ط§طھظ‡ط§ ظ†ظ‡ط§ط¦ظٹظ‹ط§ ظ…ظ† ط§ظ„ط®ط§ط¯ظ… ظˆظ„ط§ ظٹظ…ظƒظ† ط§ظ„طھط±ط§ط¬ط¹.
            {confirmDelete && <span className="block mt-2 text-zinc-400">ط§ظ„ظ…ط§ظ„ظƒ: {confirmDelete.ownerDisplayName} (@{confirmDelete.ownerUsername})</span>}
          </p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmDelete(null)} className="border-zinc-800">ط¥ظ„ط؛ط§ط،</Button>
            <Button
              variant="destructive"
              onClick={async () => {
                if (!confirmDelete) return;
                await deleteAdminStory(confirmDelete.id);
                setConfirmDelete(null);
                void load();
                toast.success('ط­ظڈط°ظپطھ ط§ظ„ط­ط§ظ„ط© ظ†ظ‡ط§ط¦ظٹظ‹ط§');
              }}
            >
              ط­ط°ظپ ظ†ظ‡ط§ط¦ظٹ
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// â”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پ Polls Tab â”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پ
function PollsTab() {
  const [polls, setPolls] = useState<Poll[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [confirmPoll, setConfirmPoll] = useState<{ open: boolean; kind: 'close' | 'delete'; id: string }>({
    open: false, kind: 'close', id: '',
  });
  
  const [form, setForm] = useState({
    question: '',
    description: '',
    options: ['', ''],
    optionImages: ['', ''],
    pollType: 'SINGLE_CHOICE',
    endDate: '',
    isAnonymous: false,
    allowAddOptions: false
  });

  const load = async () => {
    setLoading(true);
    try {
      const result = await getPolls({ page: 0, size: 50 });
      setPolls(Array.isArray(result?.content) ? result.content : []);
    } catch (e: any) {
      toast.error('طھط¹ط°ط± ط§ظ„طھط­ظ…ظٹظ„: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = () => {
    setForm({
      question: '',
      description: '',
      options: ['', ''],
      optionImages: ['', ''],
      pollType: 'SINGLE_CHOICE',
      endDate: '',
      isAnonymous: false,
      allowAddOptions: false
    });
    setCreateModalOpen(true);
  };

  const submitCreate = async () => {
    if (!form.question) {
      toast.error('ط§ظ„ط±ط¬ط§ط، ط¥ط¯ط®ط§ظ„ ط§ظ„ط³ط¤ط§ظ„');
      return;
    }
    const cleanOptions = form.options.map(o => o.trim()).filter(Boolean);
    if (cleanOptions.length < 2) {
      toast.error('ط£ط¶ظپ ط®ظٹط§ط±ظٹظ† ط¹ظ„ظ‰ ط§ظ„ط£ظ‚ظ„');
      return;
    }
    
    const cleanOptionImages = form.optionImages.slice(0, cleanOptions.length).map(i => i.trim() || null);
    while (cleanOptionImages.length < cleanOptions.length) cleanOptionImages.push(null);
    
    if (cleanOptionImages.some(Boolean) && cleanOptions.length > 4) {
      toast.error('ط§ظ„ط®ظٹط§ط±ط§طھ ط§ظ„ظ…طµظˆظ‘ط±ط© طھط¯ط¹ظ… ظ…ظ† ط®ظٹط§ط±ظٹظ† ط¥ظ„ظ‰ ط£ط±ط¨ط¹ط© ظپظ‚ط·');
      return;
    }

    try {
      await createPoll({
        question: form.question,
        description: form.description,
        options: cleanOptions,
        pollType: form.pollType,
        isAnonymous: form.isAnonymous,
        allowAddOptions: form.allowAddOptions,
        endsAt: form.endDate ? new Date(form.endDate).toISOString() : undefined,
        optionImages: cleanOptionImages,
      });
      toast.success('طھظ… ط¥ظ†ط´ط§ط، ط§ظ„ط§ط³طھط·ظ„ط§ط¹');
      setCreateModalOpen(false);
      load();
    } catch (e: any) {
      toast.error('ظپط´ظ„: ' + (e.message ?? ''));
    }
  };

  const handleClose = async (id: string) => {
    setConfirmPoll({ open: true, kind: 'close', id });
  };

  const handleDelete = async (id: string) => {
    setConfirmPoll({ open: true, kind: 'delete', id });
  };

  const confirmPollAction = async () => {
    const { kind, id } = confirmPoll;
    setConfirmPoll({ open: false, kind: 'close', id: '' });
    try {
      if (kind === 'close') {
        await closePoll(id);
        toast.success('طھظ… ط¥ط؛ظ„ط§ظ‚ ط§ظ„ط§ط³طھط·ظ„ط§ط¹');
      } else {
        await deletePoll(id);
        toast.success('طھظ… ط­ط°ظپ ط§ظ„ط§ط³طھط·ظ„ط§ط¹');
      }
      load();
    } catch (e: any) {
      toast.error('ظپط´ظ„: ' + (e.message ?? ''));
    }
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="bg-zinc-900/50 border-zinc-800">
          <CardContent className="p-6 flex items-center space-x-4 space-x-reverse">
            <div className="p-3 bg-blue-500/20 text-blue-400 rounded-lg">
              <BarChart2 className="w-6 h-6" />
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">ط¥ط¬ظ…ط§ظ„ظٹ</p>
              <h3 className="text-2xl font-bold">{polls.length}</h3>
            </div>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900/50 border-zinc-800">
          <CardContent className="p-6 flex items-center space-x-4 space-x-reverse">
            <div className="p-3 bg-emerald-500/20 text-emerald-400 rounded-lg">
              <CheckCircle className="w-6 h-6" />
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">ظ†ط´ط·ط©</p>
              <h3 className="text-2xl font-bold">{polls.filter(p => p.status === 'ACTIVE').length}</h3>
            </div>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900/50 border-zinc-800">
          <CardContent className="p-6 flex items-center space-x-4 space-x-reverse">
            <div className="p-3 bg-orange-500/20 text-orange-400 rounded-lg">
              <TrendingUp className="w-6 h-6" />
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">ط¥ط¬ظ…ط§ظ„ظٹ ط§ظ„ط£طµظˆط§طھ</p>
              <h3 className="text-2xl font-bold">{polls.reduce((sum, p) => sum + (p.totalVotes || 0), 0)}</h3>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card className="bg-zinc-900/50 border-zinc-800">
        <CardContent className="p-4 flex gap-4">
          <Button onClick={handleCreate} className="bg-emerald-600 hover:bg-emerald-700 text-white">
            <Plus className="w-4 h-4 ml-2" /> ط¥ظ†ط´ط§ط، ط§ط³طھط·ظ„ط§ط¹
          </Button>
          <Button variant="outline" onClick={load} className="border-zinc-700 hover:bg-zinc-800 text-zinc-300">
            <RefreshCw className="w-4 h-4 ml-2" /> طھط­ط¯ظٹط«
          </Button>
        </CardContent>
      </Card>

      <Card className="bg-zinc-900/50 border-zinc-800 overflow-hidden">
        {loading ? (
          <div className="p-8 space-y-4">
            <Skeleton className="h-10 w-full bg-zinc-800" />
            <Skeleton className="h-10 w-full bg-zinc-800" />
            <Skeleton className="h-10 w-full bg-zinc-800" />
          </div>
        ) : polls.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">ظ„ط§ طھظˆط¬ط¯ ط§ط³طھط·ظ„ط§ط¹ط§طھ</div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader className="bg-zinc-900/80">
                <TableRow className="border-zinc-800 hover:bg-zinc-800/50">
                  <TableHead className="text-right">ط§ظ„ط³ط¤ط§ظ„</TableHead>
                  <TableHead className="text-right">ط§ظ„ظ†ظˆط¹</TableHead>
                  <TableHead className="text-right">ط§ظ„ط­ط§ظ„ط©</TableHead>
                  <TableHead className="text-right">ط§ظ„ط£طµظˆط§طھ</TableHead>
                  <TableHead className="text-right">ظٹظ†طھظ‡ظٹ</TableHead>
                  <TableHead className="text-right">ط¥ط¬ط±ط§ط،ط§طھ</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {polls.map((p) => {
                  const typeLabel = POLL_TYPE_LABELS[p.pollType] ?? { label: p.pollType, color: 'bg-zinc-800 text-zinc-300' };
                  const statusLabel = POLL_STATUS_LABELS[p.status] ?? { label: p.status, color: 'bg-zinc-800 text-zinc-300' };
                  
                  return (
                    <TableRow key={p.id} className="border-zinc-800 hover:bg-zinc-800/50">
                      <TableCell>
                        <div className="font-semibold text-zinc-200">{p.question}</div>
                        {p.description && <div className="text-xs text-muted-foreground mt-1">{p.description}</div>}
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className={typeLabel.color}>{typeLabel.label}</Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className={statusLabel.color}>{statusLabel.label}</Badge>
                      </TableCell>
                      <TableCell>
                        <div className="font-semibold text-zinc-200">{p.totalVotes || 0}</div>
                        <div className="text-xs text-muted-foreground">{p.uniqueVoters || 0} ظ…طµظˆظ‘طھ</div>
                      </TableCell>
                      <TableCell>
                        {p.endsAt ? (
                          <span className="text-sm text-zinc-400">{formatSafeDate(p.endsAt, 'ar-EG')}</span>
                        ) : (
                          <Badge variant="outline" className="bg-zinc-800 text-zinc-400 border-zinc-700">ظ…ظپطھظˆط­</Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          {p.status === 'ACTIVE' && (
                            <TooltipProvider>
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <Button variant="outline" size="icon" className="h-8 w-8 text-orange-400 border-orange-500/30 hover:bg-orange-500/10" onClick={() => handleClose(p.id)}>
                                    <StopCircle className="h-4 w-4" />
                                  </Button>
                                </TooltipTrigger>
                                <TooltipContent>ط¥ط؛ظ„ط§ظ‚</TooltipContent>
                              </Tooltip>
                            </TooltipProvider>
                          )}
                          <Button variant="outline" size="icon" className="h-8 w-8 text-red-400 border-red-500/30 hover:bg-red-500/10 hover:text-red-300" onClick={() => handleDelete(p.id)}>
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </Card>

      <Dialog
        open={confirmPoll.open}
        onOpenChange={(open) => { if (!open) setConfirmPoll({ open: false, kind: 'close', id: '' }); }}
        title={confirmPoll.kind === 'close' ? 'ظ‡ظ„ ط£ظ†طھ ظ…طھط£ظƒط¯ ظ…ظ† ط¥ط؛ظ„ط§ظ‚ ظ‡ط°ط§ ط§ظ„ط§ط³طھط·ظ„ط§ط¹طں' : 'ظ‡ظ„ ط£ظ†طھ ظ…طھط£ظƒط¯ ظ…ظ† ط­ط°ظپ ظ‡ط°ط§ ط§ظ„ط§ط³طھط·ظ„ط§ط¹طں'}
      >
        <div className="flex items-center justify-end gap-2 py-2" dir="rtl">
          <Button variant="outline" onClick={() => setConfirmPoll({ open: false, kind: 'close', id: '' })} className="border-zinc-800 text-zinc-300 hover:bg-zinc-800">ط¥ظ„ط؛ط§ط،</Button>
          <Button onClick={confirmPollAction} className="bg-emerald-600 hover:bg-emerald-700 text-white">طھط£ظƒظٹط¯</Button>
        </div>
      </Dialog>

      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="bg-zinc-950 border-emerald-900/50 text-right" dir="rtl">
          <DialogHeader>
            <DialogTitle className="text-emerald-400">ط¥ظ†ط´ط§ط، ط§ط³طھط·ظ„ط§ط¹ ط¬ط¯ظٹط¯</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4 max-h-[70vh] overflow-y-auto px-1">
            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-300">ط§ظ„ط³ط¤ط§ظ„ *</label>
              <Input 
                value={form.question} 
                onChange={(e) => setForm({...form, question: e.target.value})} 
                placeholder="ظ…ط«ط§ظ„: ظ…ط§ ط±ط£ظٹظƒ ظپظٹ..." 
                className="bg-zinc-900 border-zinc-800 focus-visible:ring-emerald-500"
              />
            </div>
            
            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-300">ط§ظ„ظˆطµظپ (ط§ط®طھظٹط§ط±ظٹ)</label>
              <textarea 
                value={form.description} 
                onChange={(e) => setForm({...form, description: e.target.value})} 
                className={textareaClasses}
                rows={2}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-300">ط§ظ„ط®ظٹط§ط±ط§طھ *</label>
              {form.options.map((opt, idx) => (
                <div key={idx} className="flex gap-2">
                  <Input 
                    value={opt} 
                    onChange={(e) => {
                      const newOpts = [...form.options];
                      newOpts[idx] = e.target.value;
                      setForm({...form, options: newOpts});
                    }} 
                    placeholder={`ط®ظٹط§ط± ${idx + 1}`} 
                    className="bg-zinc-900 border-zinc-800 focus-visible:ring-emerald-500"
                  />
                  {form.options.length > 2 && (
                    <Button variant="outline" size="icon" className="shrink-0 border-red-500/30 text-red-400 hover:bg-red-500/10" onClick={() => {
                      const newOpts = [...form.options];
                      newOpts.splice(idx, 1);
                      const newImgs = [...form.optionImages];
                      newImgs.splice(idx, 1);
                      setForm({...form, options: newOpts, optionImages: newImgs});
                    }}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  )}
                </div>
              ))}
              <Button variant="outline" size="sm" className="mt-2 text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/10" onClick={() => {
                setForm({...form, options: [...form.options, ''], optionImages: [...form.optionImages, '']});
              }}>
                <Plus className="w-4 h-4 ml-2" /> ط¥ط¶ط§ظپط© ط®ظٹط§ط±
              </Button>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-300">طµظˆط± ط§ظ„ط®ظٹط§ط±ط§طھ (ط§ط®طھظٹط§ط±ظٹ)</label>
              <p className="text-xs text-muted-foreground mb-2">ط±ط§ط¨ط· طµظˆط±ط© ظ„ظƒظ„ ط®ظٹط§ط± ط¨ظ†ظپط³ ط§ظ„طھط±طھظٹط¨. ط§طھط±ظƒظ‡ ظپط§ط±ط؛ظ‹ط§ ظ„ظ„ط®ظٹط§ط± ط§ظ„ظ†طµظٹ.</p>
              {form.optionImages.map((img, idx) => (
                idx < form.options.length && (
                  <div key={idx} className="flex gap-2 mb-2">
                    <Input 
                      value={img} 
                      onChange={(e) => {
                        const newImgs = [...form.optionImages];
                        newImgs[idx] = e.target.value;
                        setForm({...form, optionImages: newImgs});
                      }} 
                      placeholder={`ط±ط§ط¨ط· طµظˆط±ط© ط§ظ„ط®ظٹط§ط± ${idx + 1}`} 
                      className="bg-zinc-900 border-zinc-800"
                    />
                  </div>
                )
              ))}
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium text-zinc-300">ط§ظ„ظ†ظˆط¹</label>
                <Select value={form.pollType} onValueChange={(v) => setForm({...form, pollType: v})}>
                  <SelectTrigger className="bg-zinc-900 border-zinc-800">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="bg-zinc-900 border-zinc-800">
                    {Object.entries(POLL_TYPE_LABELS).map(([v, t]) => (
                      <SelectItem key={v} value={v}>{t.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              
              <div className="space-y-2">
                <label className="text-sm font-medium text-zinc-300">طھط§ط±ظٹط® ط§ظ„ط§ظ†طھظ‡ط§ط،</label>
                <Input 
                  type="datetime-local" 
                  value={form.endDate} 
                  onChange={(e) => setForm({...form, endDate: e.target.value})}
                  className="bg-zinc-900 border-zinc-800 w-full color-scheme-dark"
                />
              </div>
            </div>

            <div className="flex items-center gap-6 pt-2">
              <div className="flex items-center gap-2">
                <Switch 
                  checked={form.isAnonymous} 
                  onCheckedChange={(c) => setForm({...form, isAnonymous: c})} 
                />
                <label className="text-sm text-zinc-300">ظ…ط¬ظ‡ظˆظ„</label>
              </div>
              <div className="flex items-center gap-2">
                <Switch 
                  checked={form.allowAddOptions} 
                  onCheckedChange={(c) => setForm({...form, allowAddOptions: c})} 
                />
                <label className="text-sm text-zinc-300">ط§ظ„ط³ظ…ط§ط­ ط¨ط¥ط¶ط§ظپط© ط®ظٹط§ط±ط§طھ</label>
              </div>
            </div>

          </div>
          <DialogFooter className="sm:justify-start">
            <Button onClick={submitCreate} className="bg-emerald-600 hover:bg-emerald-700 text-white">ط¥ظ†ط´ط§ط،</Button>
            <Button variant="outline" onClick={() => setCreateModalOpen(false)} className="border-zinc-800 text-zinc-300 hover:bg-zinc-800">ط¥ظ„ط؛ط§ط،</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// â”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پ Events Tab â”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پ
function EventsTab() {
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [confirmEvent, setConfirmEvent] = useState<{ open: boolean; kind: 'cancel' | 'delete'; id: string }>({
    open: false, kind: 'cancel', id: '',
  });
  
  const [form, setForm] = useState({
    title: '',
    description: '',
    locationName: '',
    startDate: '',
    endDate: '',
    eventType: 'MEETING',
    visibility: 'PUBLIC',
    maxAttendees: '' as string | number,
    rsvpEnabled: true,
  });

  const load = async () => {
    setLoading(true);
    try {
      const result = await getEvents({ page: 0, size: 50 });
      setEvents(Array.isArray(result?.content) ? result.content : []);
    } catch (e: any) {
      toast.error('طھط¹ط°ط± ط§ظ„طھط­ظ…ظٹظ„: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = () => {
    setForm({
      title: '',
      description: '',
      locationName: '',
      startDate: '',
      endDate: '',
      eventType: 'MEETING',
      visibility: 'PUBLIC',
      maxAttendees: '',
      rsvpEnabled: true,
    });
    setCreateModalOpen(true);
  };

  const submitCreate = async () => {
    if (!form.title || !form.startDate) {
      toast.error('ط§ظ„ط¹ظ†ظˆط§ظ† ظˆطھط§ط±ظٹط® ط§ظ„ط¨ط¯ط، ظ…ط·ظ„ظˆط¨ط§ظ†');
      return;
    }
    try {
      await createEvent({
        title: form.title,
        description: form.description,
        locationName: form.locationName,
        startsAt: new Date(form.startDate).toISOString(),
        endsAt: form.endDate ? new Date(form.endDate).toISOString() : undefined,
        eventType: form.eventType,
        visibility: form.visibility,
        maxAttendees: form.maxAttendees ? Number(form.maxAttendees) : undefined,
        rsvpEnabled: form.rsvpEnabled,
      });
      toast.success('طھظ… ط¥ظ†ط´ط§ط، ط§ظ„ط­ط¯ط«');
      setCreateModalOpen(false);
      load();
    } catch (e: any) {
      toast.error('ظپط´ظ„: ' + (e.message ?? ''));
    }
  };

  const handleCancel = async (id: string) => {
    setConfirmEvent({ open: true, kind: 'cancel', id });
  };

  const handleDelete = async (id: string) => {
    setConfirmEvent({ open: true, kind: 'delete', id });
  };

  const confirmEventAction = async () => {
    const { kind, id } = confirmEvent;
    setConfirmEvent({ open: false, kind: 'cancel', id: '' });
    try {
      if (kind === 'cancel') {
        await cancelEvent(id, 'ط¥ظ„ط؛ط§ط، ط¥ط¯ط§ط±ظٹ');
        toast.success('طھظ… ط¥ظ„ط؛ط§ط، ط§ظ„ط­ط¯ط«');
      } else {
        await deleteEvent(id);
        toast.success('طھظ… ط­ط°ظپ ط§ظ„ط­ط¯ط«');
      }
      load();
    } catch (e: any) {
      toast.error('ظپط´ظ„: ' + (e.message ?? ''));
    }
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <Card className="bg-zinc-900/50 border-zinc-800">
          <CardContent className="p-6 flex items-center space-x-4 space-x-reverse">
            <div className="p-3 bg-blue-500/20 text-blue-400 rounded-lg"><Calendar className="w-6 h-6" /></div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">ط¥ط¬ظ…ط§ظ„ظٹ</p>
              <h3 className="text-2xl font-bold">{events.length}</h3>
            </div>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900/50 border-zinc-800">
          <CardContent className="p-6 flex items-center space-x-4 space-x-reverse">
            <div className="p-3 bg-orange-500/20 text-orange-400 rounded-lg"><Calendar className="w-6 h-6" /></div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">ظ…ط¬ط¯ظˆظ„</p>
              <h3 className="text-2xl font-bold">{events.filter(e => e.status === 'SCHEDULED').length}</h3>
            </div>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900/50 border-zinc-800">
          <CardContent className="p-6 flex items-center space-x-4 space-x-reverse">
            <div className="p-3 bg-emerald-500/20 text-emerald-400 rounded-lg"><Flame className="w-6 h-6" /></div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">ظ…ط¨ط§ط´ط±</p>
              <h3 className="text-2xl font-bold">{events.filter(e => e.status === 'LIVE').length}</h3>
            </div>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900/50 border-zinc-800">
          <CardContent className="p-6 flex items-center space-x-4 space-x-reverse">
            <div className="p-3 bg-cyan-500/20 text-cyan-400 rounded-lg"><CheckCircle className="w-6 h-6" /></div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">ط¥ط¬ظ…ط§ظ„ظٹ ط§ظ„ط­ط¶ظˆط±</p>
              <h3 className="text-2xl font-bold">{events.reduce((s, e) => s + (e.currentAttendees || 0), 0)}</h3>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card className="bg-zinc-900/50 border-zinc-800">
        <CardContent className="p-4 flex gap-4">
          <Button onClick={handleCreate} className="bg-emerald-600 hover:bg-emerald-700 text-white">
            <Plus className="w-4 h-4 ml-2" /> ط¥ظ†ط´ط§ط، ط­ط¯ط«
          </Button>
          <Button variant="outline" onClick={load} className="border-zinc-700 hover:bg-zinc-800 text-zinc-300">
            <RefreshCw className="w-4 h-4 ml-2" /> طھط­ط¯ظٹط«
          </Button>
        </CardContent>
      </Card>

      <Card className="bg-zinc-900/50 border-zinc-800 overflow-hidden">
        {loading ? (
          <div className="p-8 space-y-4">
            <Skeleton className="h-10 w-full bg-zinc-800" />
            <Skeleton className="h-10 w-full bg-zinc-800" />
          </div>
        ) : events.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">ظ„ط§ طھظˆط¬ط¯ ط£ط­ط¯ط§ط«</div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader className="bg-zinc-900/80">
                <TableRow className="border-zinc-800 hover:bg-zinc-800/50">
                  <TableHead className="text-right">ط§ظ„ط­ط¯ط«</TableHead>
                  <TableHead className="text-right">ط§ظ„ظ†ظˆط¹</TableHead>
                  <TableHead className="text-right">ط§ظ„ط¸ظ‡ظˆط±</TableHead>
                  <TableHead className="text-right">ط§ظ„ط­ط§ظ„ط©</TableHead>
                  <TableHead className="text-right">ط§ظ„طھط§ط±ظٹط®</TableHead>
                  <TableHead className="text-right">ط§ظ„ط­ط¶ظˆط±</TableHead>
                  <TableHead className="text-right">ط¥ط¬ط±ط§ط،ط§طھ</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {events.map((e) => {
                  const typeLabel = EVENT_TYPE_LABELS[e.eventType] ?? { label: e.eventType, color: 'bg-zinc-800 text-zinc-300' };
                  const statusLabel = EVENT_STATUS_LABELS[e.status] ?? { label: e.status, color: 'bg-zinc-800 text-zinc-300' };
                  
                  return (
                    <TableRow key={e.id} className="border-zinc-800 hover:bg-zinc-800/50">
                      <TableCell>
                        <div className="font-semibold text-zinc-200">{e.title}</div>
                        {e.locationName && (
                          <div className="text-xs text-muted-foreground mt-1 flex items-center gap-1">
                            <MapPin className="w-3 h-3" /> {e.locationName}
                          </div>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className={typeLabel.color}>{typeLabel.label}</Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className="bg-zinc-800 text-zinc-300 border-zinc-700">{EVENT_VISIBILITY_LABELS[e.visibility] ?? e.visibility}</Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className={statusLabel.color}>{statusLabel.label}</Badge>
                      </TableCell>
                      <TableCell>
                        <div className="text-sm text-zinc-300">{formatSafeDate(e.startsAt, 'ar-EG')}</div>
                        <div className="text-xs text-muted-foreground">{formatSafeTime(e.startsAt)}</div>
                      </TableCell>
                      <TableCell>
                        <span className="text-sm text-zinc-300">
                          {e.currentAttendees || 0}{e.maxAttendees ? ` / ${e.maxAttendees}` : ''}
                        </span>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          {e.status !== 'CANCELLED' && e.status !== 'ENDED' && (
                            <Button variant="outline" size="sm" className="h-8 text-orange-400 border-orange-500/30 hover:bg-orange-500/10" onClick={() => handleCancel(e.id)}>
                              ط¥ظ„ط؛ط§ط،
                            </Button>
                          )}
                          <Button variant="outline" size="icon" className="h-8 w-8 text-red-400 border-red-500/30 hover:bg-red-500/10 hover:text-red-300" onClick={() => handleDelete(e.id)}>
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </Card>

      <Dialog
        open={confirmEvent.open}
        onOpenChange={(open) => { if (!open) setConfirmEvent({ open: false, kind: 'cancel', id: '' }); }}
        title={confirmEvent.kind === 'cancel' ? 'ظ‡ظ„ ط£ظ†طھ ظ…طھط£ظƒط¯ ظ…ظ† ط¥ظ„ط؛ط§ط، ظ‡ط°ط§ ط§ظ„ط­ط¯ط«طں' : 'ظ‡ظ„ ط£ظ†طھ ظ…طھط£ظƒط¯ ظ…ظ† ط­ط°ظپ ظ‡ط°ط§ ط§ظ„ط­ط¯ط«طں'}
      >
        <div className="flex items-center justify-end gap-2 py-2" dir="rtl">
          <Button variant="outline" onClick={() => setConfirmEvent({ open: false, kind: 'cancel', id: '' })} className="border-zinc-800 text-zinc-300 hover:bg-zinc-800">ط¥ظ„ط؛ط§ط،</Button>
          <Button onClick={confirmEventAction} className="bg-emerald-600 hover:bg-emerald-700 text-white">طھط£ظƒظٹط¯</Button>
        </div>
      </Dialog>

      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="bg-zinc-950 border-emerald-900/50 text-right" dir="rtl">
          <DialogHeader>
            <DialogTitle className="text-emerald-400">ط¥ظ†ط´ط§ط، ط­ط¯ط« ط¬ط¯ظٹط¯</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4 max-h-[70vh] overflow-y-auto px-1">
            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-300">ط§ظ„ط¹ظ†ظˆط§ظ† *</label>
              <Input 
                value={form.title} 
                onChange={(e) => setForm({...form, title: e.target.value})} 
                placeholder="ط¹ظ†ظˆط§ظ† ط§ظ„ط­ط¯ط«" 
                className="bg-zinc-900 border-zinc-800"
              />
            </div>
            
            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-300">ط§ظ„ظˆطµظپ</label>
              <textarea 
                value={form.description} 
                onChange={(e) => setForm({...form, description: e.target.value})} 
                className={textareaClasses}
                rows={3}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium text-zinc-300">طھط§ط±ظٹط® ط§ظ„ط¨ط¯ط، *</label>
                <Input 
                  type="datetime-local" 
                  value={form.startDate} 
                  onChange={(e) => setForm({...form, startDate: e.target.value})}
                  className="bg-zinc-900 border-zinc-800 color-scheme-dark"
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium text-zinc-300">طھط§ط±ظٹط® ط§ظ„ط§ظ†طھظ‡ط§ط،</label>
                <Input 
                  type="datetime-local" 
                  value={form.endDate} 
                  onChange={(e) => setForm({...form, endDate: e.target.value})}
                  className="bg-zinc-900 border-zinc-800 color-scheme-dark"
                />
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-300">ط§ظ„ظ…ظƒط§ظ†</label>
              <Input 
                value={form.locationName} 
                onChange={(e) => setForm({...form, locationName: e.target.value})} 
                placeholder="ط§ط³ظ… ط§ظ„ظ…ظƒط§ظ† (ط§ط®طھظٹط§ط±ظٹ)" 
                className="bg-zinc-900 border-zinc-800"
              />
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium text-zinc-300">ط§ظ„ظ†ظˆط¹</label>
                <Select value={form.eventType} onValueChange={(v) => setForm({...form, eventType: v})}>
                  <SelectTrigger className="bg-zinc-900 border-zinc-800">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="bg-zinc-900 border-zinc-800">
                    {Object.entries(EVENT_TYPE_LABELS).map(([v, t]) => (
                      <SelectItem key={v} value={v}>{t.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium text-zinc-300">ط§ظ„ط¸ظ‡ظˆط±</label>
                <Select value={form.visibility} onValueChange={(v) => setForm({...form, visibility: v})}>
                  <SelectTrigger className="bg-zinc-900 border-zinc-800">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="bg-zinc-900 border-zinc-800">
                    {Object.entries(EVENT_VISIBILITY_LABELS).map(([v, l]) => (
                      <SelectItem key={v} value={v}>{l}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium text-zinc-300">ط§ظ„ط­ط¯ ط§ظ„ط£ظ‚طµظ‰ ظ„ظ„ط­ط¶ظˆط±</label>
                <Input 
                  type="number"
                  min="1"
                  value={form.maxAttendees} 
                  onChange={(e) => setForm({...form, maxAttendees: e.target.value})} 
                  placeholder="ط¨ظ„ط§ ط­ط¯" 
                  className="bg-zinc-900 border-zinc-800"
                />
              </div>
            </div>

            <div className="flex items-center gap-2 pt-2">
              <Switch 
                checked={form.rsvpEnabled} 
                onCheckedChange={(c) => setForm({...form, rsvpEnabled: c})} 
              />
              <label className="text-sm text-zinc-300">ط§ظ„ط³ظ…ط§ط­ ط¨ظ€ RSVP</label>
            </div>

          </div>
          <DialogFooter className="sm:justify-start">
            <Button onClick={submitCreate} className="bg-emerald-600 hover:bg-emerald-700 text-white">ط¥ظ†ط´ط§ط،</Button>
            <Button variant="outline" onClick={() => setCreateModalOpen(false)} className="border-zinc-800 text-zinc-300 hover:bg-zinc-800">ط¥ظ„ط؛ط§ط،</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// â”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پ Hashtags Tab â”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پ
function HashtagsTab() {
  const [trending, setTrending] = useState<Hashtag[]>([]);
  const [popular, setPopular] = useState<Hashtag[]>([]);
  const [loading, setLoading] = useState(false);
  const [view, setView] = useState<'trending' | 'popular'>('trending');
  const [confirmHashtag, setConfirmHashtag] = useState<{ open: boolean; id: string }>({ open: false, id: '' });

  const load = async () => {
    setLoading(true);
    try {
      const [t, p] = await Promise.all([getTrendingHashtags(100), getPopularHashtags(100)]);
      setTrending(Array.isArray(t) ? t : []);
      setPopular(Array.isArray(p) ? p : []);
    } catch (e: any) {
      toast.error('طھط¹ط°ط± ط§ظ„طھط­ظ…ظٹظ„: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleBlock = async (id: string) => {
    setConfirmHashtag({ open: true, id });
  };

  const confirmBlockHashtag = async () => {
    const { id } = confirmHashtag;
    setConfirmHashtag({ open: false, id: '' });
    try {
      await blockHashtag(id, 'ط¥ظٹظ‚ط§ظپ ط¥ط¯ط§ط±ظٹ');
      toast.success('طھظ… ط¥ظٹظ‚ط§ظپ ط§ظ„ظ‡ط§ط´طھط§ط¬');
      load();
    } catch (e: any) {
      toast.error('ظپط´ظ„: ' + (e.message ?? ''));
    }
  };

  const handleUnblock = async (id: string) => {
    try {
      await unblockHashtag(id);
      toast.success('طھظ… طھظپط¹ظٹظ„ ط§ظ„ظ‡ط§ط´طھط§ط¬');
      load();
    } catch (e: any) {
      toast.error('ظپط´ظ„: ' + (e.message ?? ''));
    }
  };

  const data = view === 'trending' ? trending : popular;

  return (
    <div className="space-y-6">
      <Card className="bg-zinc-900/50 border-zinc-800">
        <CardContent className="p-4 flex gap-4">
          <Button 
            variant={view === 'trending' ? 'default' : 'outline'} 
            className={cn(view === 'trending' ? 'bg-emerald-600 hover:bg-emerald-700 text-white' : 'border-zinc-700 text-zinc-300 hover:bg-zinc-800')} 
            onClick={() => setView('trending')}
          >
            <Flame className="w-4 h-4 ml-2" /> طھط±ظ†ط¯ ({trending.length})
          </Button>
          <Button 
            variant={view === 'popular' ? 'default' : 'outline'} 
            className={cn(view === 'popular' ? 'bg-emerald-600 hover:bg-emerald-700 text-white' : 'border-zinc-700 text-zinc-300 hover:bg-zinc-800')}
            onClick={() => setView('popular')}
          >
            <TrendingUp className="w-4 h-4 ml-2" /> ط§ظ„ط£ظƒط«ط± ط§ط³طھط®ط¯ط§ظ…ط§ظ‹ ({popular.length})
          </Button>
          <Button variant="outline" onClick={load} className="border-zinc-700 hover:bg-zinc-800 text-zinc-300">
            <RefreshCw className="w-4 h-4 ml-2" /> طھط­ط¯ظٹط«
          </Button>
        </CardContent>
      </Card>

      <Card className="bg-zinc-900/50 border-zinc-800 overflow-hidden">
        {loading ? (
          <div className="p-8 space-y-4">
            <Skeleton className="h-10 w-full bg-zinc-800" />
            <Skeleton className="h-10 w-full bg-zinc-800" />
          </div>
        ) : data.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">ظ„ط§ طھظˆط¬ط¯ ظ‡ط§ط´طھط§ط¬ط§طھ</div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader className="bg-zinc-900/80">
                <TableRow className="border-zinc-800 hover:bg-zinc-800/50">
                  <TableHead className="text-right">ط§ظ„ظ‡ط§ط´طھط§ط¬</TableHead>
                  <TableHead className="text-right">ط§ظ„ط§ط³طھط®ط¯ط§ظ…</TableHead>
                  <TableHead className="text-right">ط§ظ„ظ…ظ†ط´ظˆط±ط§طھ</TableHead>
                  <TableHead className="text-right">ط§ظ„ظ‚طµطµ</TableHead>
                  <TableHead className="text-right">ظ…ط³طھط®ط¯ظ…ظˆظ† ظپط±ظٹط¯ظˆظ†</TableHead>
                  <TableHead className="text-right">ط§ظ„ظ†ظ‚ط§ط· (Score)</TableHead>
                  <TableHead className="text-right">ط¥ط¬ط±ط§ط،ط§طھ</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.map((h) => {
                  const tagName = (h as any).tagName || (h as any).tag || '';
                  return (
                    <TableRow key={h.id} className="border-zinc-800 hover:bg-zinc-800/50">
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-zinc-200">#{tagName}</span>
                          {h.isTrending && <Badge variant="outline" className="bg-orange-500/20 text-orange-400 border-orange-500/30">طھط±ظ†ط¯ <Flame className="w-3 h-3 mr-1" /></Badge>}
                          {h.isBlocked && <Badge variant="outline" className="bg-red-500/20 text-red-400 border-red-500/30">ظ…ط­ط¸ظˆط±</Badge>}
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant="secondary" className="bg-blue-500/20 text-blue-400">{h.usageCount || 0}</Badge>
                      </TableCell>
                      <TableCell className="text-zinc-300">{h.postsCount || 0}</TableCell>
                      <TableCell className="text-zinc-300">{h.storiesCount || 0}</TableCell>
                      <TableCell className="text-zinc-300">{h.uniqueUsers || 0}</TableCell>
                      <TableCell>
                        <code className="text-xs bg-zinc-800 text-emerald-400 px-2 py-1 rounded">
                          {Number((h as any).trendingScore ?? (h as any).trendScore ?? 0).toFixed(2)}
                        </code>
                      </TableCell>
                      <TableCell>
                        {h.isBlocked ? (
                          <Button size="sm" variant="outline" className="text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/10" onClick={() => handleUnblock(h.id)}>طھظپط¹ظٹظ„</Button>
                        ) : (
                          <Button size="sm" variant="outline" className="text-red-400 border-red-500/30 hover:bg-red-500/10" onClick={() => handleBlock(h.id)}>ط­ط¸ط±</Button>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </Card>

      <Dialog
        open={confirmHashtag.open}
        onOpenChange={(open) => { if (!open) setConfirmHashtag({ open: false, id: '' }); }}
        title="ظ‡ظ„ ط£ظ†طھ ظ…طھط£ظƒط¯ ظ…ظ† ط­ط¸ط± ظ‡ط°ط§ ط§ظ„ظ‡ط§ط´طھط§ط¬طں"
      >
        <div className="flex items-center justify-end gap-2 py-2" dir="rtl">
          <Button variant="outline" onClick={() => setConfirmHashtag({ open: false, id: '' })} className="border-zinc-800 text-zinc-300 hover:bg-zinc-800">ط¥ظ„ط؛ط§ط،</Button>
          <Button onClick={confirmBlockHashtag} className="bg-emerald-600 hover:bg-emerald-700 text-white">طھط£ظƒظٹط¯</Button>
        </div>
      </Dialog>
    </div>
  );
}

// â”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پ Stickers Tab â”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پâ”پ
function StickersTab() {
  const [packs, setPacks] = useState<StickerPack[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [confirmPack, setConfirmPack] = useState<{ open: boolean; id: string }>({ open: false, id: '' });
  
  const [form, setForm] = useState({
    name: '',
    description: '',
    coverMediaKey: '',
    isOfficial: false,
    isFree: true,
    priceCents: 0
  });

  const load = async () => {
    setLoading(true);
    try {
      const result = await getStickerPacks(false);
      setPacks(Array.isArray(result) ? result : []);
    } catch (e: any) {
      toast.error('طھط¹ط°ط± ط§ظ„طھط­ظ…ظٹظ„: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = () => {
    setForm({
      name: '',
      description: '',
      coverMediaKey: '',
      isOfficial: false,
      isFree: true,
      priceCents: 0
    });
    setCreateModalOpen(true);
  };

  const submitCreate = async () => {
    if (!form.name || !form.coverMediaKey) {
      toast.error('ط§ظ„ط§ط³ظ… ظˆظ…ظپطھط§ط­ طµظˆط±ط© ط§ظ„ط؛ظ„ط§ظپ ظ…ط·ظ„ظˆط¨ط§ظ†');
      return;
    }
    try {
      await createStickerPack({
        name: form.name,
        description: form.description,
        coverMediaKey: form.coverMediaKey,
        isOfficial: form.isOfficial,
        isFree: form.isFree,
        priceCents: form.priceCents,
      });
      toast.success('طھظ… ط¥ظ†ط´ط§ط، ط­ط²ظ…ط© ط§ظ„ظ…ظ„طµظ‚ط§طھ');
      setCreateModalOpen(false);
      load();
    } catch (e: any) {
      toast.error('ظپط´ظ„: ' + (e.message ?? ''));
    }
  };

  const handlePublish = async (id: string) => {
    try {
      await publishStickerPack(id);
      toast.success('طھظ… ط§ظ„ظ†ط´ط±');
      load();
    } catch (e: any) {
      toast.error('ظپط´ظ„: ' + (e.message ?? ''));
    }
  };

  const handleDelete = async (id: string) => {
    setConfirmPack({ open: true, id });
  };

  const confirmDeletePack = async () => {
    const { id } = confirmPack;
    setConfirmPack({ open: false, id: '' });
    try {
      await deleteStickerPack(id);
      toast.success('طھظ… ط§ظ„ط­ط°ظپ');
      load();
    } catch (e: any) {
      toast.error('ظپط´ظ„: ' + (e.message ?? ''));
    }
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="bg-zinc-900/50 border-zinc-800">
          <CardContent className="p-6 flex items-center space-x-4 space-x-reverse">
            <div className="p-3 bg-blue-500/20 text-blue-400 rounded-lg"><ImageIcon className="w-6 h-6" /></div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">ط¥ط¬ظ…ط§ظ„ظٹ</p>
              <h3 className="text-2xl font-bold">{packs.length}</h3>
            </div>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900/50 border-zinc-800">
          <CardContent className="p-6 flex items-center space-x-4 space-x-reverse">
            <div className="p-3 bg-purple-500/20 text-purple-400 rounded-lg"><Check className="w-6 h-6" /></div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">ط±ط³ظ…ظٹط©</p>
              <h3 className="text-2xl font-bold">{packs.filter(p => p.isOfficial).length}</h3>
            </div>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900/50 border-zinc-800">
          <CardContent className="p-6 flex items-center space-x-4 space-x-reverse">
            <div className="p-3 bg-emerald-500/20 text-emerald-400 rounded-lg"><CheckCircle className="w-6 h-6" /></div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">ظ…ظ†ط´ظˆط±ط©</p>
              <h3 className="text-2xl font-bold">{packs.filter(p => p.isPublished).length}</h3>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card className="bg-zinc-900/50 border-zinc-800">
        <CardContent className="p-4 flex gap-4">
          <Button onClick={handleCreate} className="bg-emerald-600 hover:bg-emerald-700 text-white">
            <Plus className="w-4 h-4 ml-2" /> ط¥ظ†ط´ط§ط، ط­ط²ظ…ط© ظ…ظ„طµظ‚ط§طھ
          </Button>
          <Button variant="outline" onClick={load} className="border-zinc-700 hover:bg-zinc-800 text-zinc-300">
            <RefreshCw className="w-4 h-4 ml-2" /> طھط­ط¯ظٹط«
          </Button>
        </CardContent>
      </Card>

      <Card className="bg-zinc-900/50 border-zinc-800 overflow-hidden">
        {loading ? (
          <div className="p-8 space-y-4">
            <Skeleton className="h-10 w-full bg-zinc-800" />
            <Skeleton className="h-10 w-full bg-zinc-800" />
          </div>
        ) : packs.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">ظ„ط§ طھظˆط¬ط¯ ط­ط²ظ… ظ…ظ„طµظ‚ط§طھ</div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader className="bg-zinc-900/80">
                <TableRow className="border-zinc-800 hover:bg-zinc-800/50">
                  <TableHead className="text-right">ط§ظ„ط§ط³ظ…</TableHead>
                  <TableHead className="text-right">ط§ظ„ظˆطµظپ</TableHead>
                  <TableHead className="text-right">ط§ظ„ط³ط¹ط±</TableHead>
                  <TableHead className="text-right">ط§ظ„ظ…ظ„طµظ‚ط§طھ</TableHead>
                  <TableHead className="text-right">ط§ظ„طھظ†ط²ظٹظ„ط§طھ</TableHead>
                  <TableHead className="text-right">ط§ظ„ط­ط§ظ„ط©</TableHead>
                  <TableHead className="text-right">ط¥ط¬ط±ط§ط،ط§طھ</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {packs.map((p) => (
                  <TableRow key={p.id} className="border-zinc-800 hover:bg-zinc-800/50">
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-zinc-200">{p.name}</span>
                        {p.isOfficial && <Badge variant="outline" className="bg-purple-500/20 text-purple-400 border-purple-500/30"><Check className="w-3 h-3 mr-1" /> ط±ط³ظ…ظٹ</Badge>}
                      </div>
                    </TableCell>
                    <TableCell>
                      {p.description ? (
                        <TooltipProvider>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <span className="text-zinc-300 truncate max-w-[150px] inline-block cursor-help">{p.description}</span>
                            </TooltipTrigger>
                            <TooltipContent className="bg-zinc-800 border-zinc-700">{p.description}</TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                      ) : (
                        <span className="text-zinc-500">â€”</span>
                      )}
                    </TableCell>
                    <TableCell>
                      {p.isFree ? (
                        <Badge variant="outline" className="bg-emerald-500/20 text-emerald-400 border-emerald-500/30">ظ…ط¬ط§ظ†ظٹ</Badge>
                      ) : (
                        <span className="font-semibold text-zinc-200">{((p.priceCents || 0) / 100).toFixed(2)} {p.currency}</span>
                      )}
                    </TableCell>
                    <TableCell className="text-zinc-300">{p.stickerCount || 0}</TableCell>
                    <TableCell className="text-zinc-300">{p.totalDownloads || 0}</TableCell>
                    <TableCell>
                      {p.isPublished ? (
                        <Badge variant="outline" className="bg-emerald-500/20 text-emerald-400 border-emerald-500/30">ظ…ظ†ط´ظˆط±</Badge>
                      ) : (
                        <Badge variant="outline" className="bg-zinc-800 text-zinc-400 border-zinc-700">ظ…ط³ظˆط¯ط©</Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        {!p.isPublished && (
                          <Button size="sm" variant="outline" className="text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/10" onClick={() => handlePublish(p.id)}>ظ†ط´ط±</Button>
                        )}
                        <Button variant="outline" size="icon" className="h-8 w-8 text-red-400 border-red-500/30 hover:bg-red-500/10 hover:text-red-300" onClick={() => handleDelete(p.id)}>
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </Card>

      <Dialog
        open={confirmPack.open}
        onOpenChange={(open) => { if (!open) setConfirmPack({ open: false, id: '' }); }}
        title="ظ‡ظ„ ط£ظ†طھ ظ…طھط£ظƒط¯ ظ…ظ† ط­ط°ظپ ظ‡ط°ظ‡ ط§ظ„ط­ط²ظ…ط©طں"
      >
        <div className="flex items-center justify-end gap-2 py-2" dir="rtl">
          <Button variant="outline" onClick={() => setConfirmPack({ open: false, id: '' })} className="border-zinc-800 text-zinc-300 hover:bg-zinc-800">ط¥ظ„ط؛ط§ط،</Button>
          <Button onClick={confirmDeletePack} className="bg-emerald-600 hover:bg-emerald-700 text-white">طھط£ظƒظٹط¯</Button>
        </div>
      </Dialog>

      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="bg-zinc-950 border-emerald-900/50 text-right" dir="rtl">
          <DialogHeader>
            <DialogTitle className="text-emerald-400">ط¥ظ†ط´ط§ط، ط­ط²ظ…ط© ظ…ظ„طµظ‚ط§طھ</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-300">ط§ظ„ط§ط³ظ… *</label>
              <Input 
                value={form.name} 
                onChange={(e) => setForm({...form, name: e.target.value})} 
                placeholder="ط§ط³ظ… ط§ظ„ط­ط²ظ…ط©" 
                className="bg-zinc-900 border-zinc-800"
              />
            </div>
            
            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-300">ط§ظ„ظˆطµظپ</label>
              <textarea 
                value={form.description} 
                onChange={(e) => setForm({...form, description: e.target.value})} 
                className={textareaClasses}
                rows={2}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-zinc-300">ظ…ظپطھط§ط­ طµظˆط±ط© ط§ظ„ط؛ظ„ط§ظپ *</label>
              <Input 
                value={form.coverMediaKey} 
                onChange={(e) => setForm({...form, coverMediaKey: e.target.value})} 
                placeholder="media-key" 
                className="bg-zinc-900 border-zinc-800"
              />
            </div>

            <div className="flex items-center gap-6 pt-2">
              <div className="flex items-center gap-2">
                <Switch 
                  checked={form.isOfficial} 
                  onCheckedChange={(c) => setForm({...form, isOfficial: c})} 
                />
                <label className="text-sm text-zinc-300">ط±ط³ظ…ظٹط©</label>
              </div>
              <div className="flex items-center gap-2">
                <Switch 
                  checked={form.isFree} 
                  onCheckedChange={(c) => setForm({...form, isFree: c})} 
                />
                <label className="text-sm text-zinc-300">ظ…ط¬ط§ظ†ظٹط©</label>
              </div>
            </div>

            {!form.isFree && (
              <div className="space-y-2">
                <label className="text-sm font-medium text-zinc-300">ط§ظ„ط³ط¹ط± (ط³ظ†طھ)</label>
                <Input 
                  type="number"
                  min="0"
                  value={form.priceCents} 
                  onChange={(e) => setForm({...form, priceCents: Number(e.target.value)})} 
                  className="bg-zinc-900 border-zinc-800"
                />
              </div>
            )}
          </div>
          <DialogFooter className="sm:justify-start">
            <Button onClick={submitCreate} className="bg-emerald-600 hover:bg-emerald-700 text-white">ط¥ظ†ط´ط§ط،</Button>
            <Button variant="outline" onClick={() => setCreateModalOpen(false)} className="border-zinc-800 text-zinc-300 hover:bg-zinc-800">ط¥ظ„ط؛ط§ط،</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
