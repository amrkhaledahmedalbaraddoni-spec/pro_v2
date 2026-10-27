import { useCallback, useState } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from './ui/card';
import { Avatar, AvatarFallback } from './ui/avatar';
import { Badge } from './ui/badge';
import { Button } from './ui/button';
import { Skeleton } from './ui/skeleton';
import { getPresenceOnlineWithFallback } from '../api';
import { usePolling } from '../hooks/usePolling';
import { Wifi, RefreshCw, AlertCircle } from 'lucide-react';
import { Tooltip, TooltipContent, TooltipTrigger, TooltipProvider } from './ui/tooltip';

export default function OnlinePresenceCard({ compact = false }: { compact?: boolean }) {
  const [users, setUsers] = useState<any[]>([]);
  const [count, setCount] = useState(0);
  const [source, setSource] = useState<'presence' | 'fallback'>('presence');
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const result = await getPresenceOnlineWithFallback();
      setUsers(result.users);
      setCount(result.count);
      setSource(result.source);
      setNote(result.note);
      setError('');
    } catch (e: any) {
      setError(e?.message || 'Failed to fetch presence data.');
    } finally {
      setLoading(false);
    }
  }, []);

  usePolling(load, 15000);

  const online = users.slice(0, compact ? 5 : 8);

  return (
    <Card className="animate-slide-up flex flex-col h-full">
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <div className="flex items-center gap-2">
          <Wifi className="h-5 w-5 text-primary animate-pulse" />
          <CardTitle className="text-base">Online Presence</CardTitle>
          {source === 'presence' ? (
            <Badge variant="success" className="ml-2">Live</Badge>
          ) : (
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger>
                  <Badge variant="warning" className="ml-2">Fallback</Badge>
                </TooltipTrigger>
                <TooltipContent>{note}</TooltipContent>
              </Tooltip>
            </TooltipProvider>
          )}
        </div>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" onClick={() => void load()} disabled={loading}>
                <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Refresh Data</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </CardHeader>
      
      <CardContent className="flex-1 flex flex-col">
        <div className="mb-4">
          <div className="text-3xl font-bold text-primary flex items-center gap-2">
            {loading && users.length === 0 ? <Skeleton className="h-8 w-16" /> : count}
          </div>
          <p className="text-xs text-muted-foreground mt-1">Users currently online</p>
        </div>

        {error && (
          <div className="flex items-start gap-3 bg-destructive/10 text-destructive p-3 rounded-md mb-4">
            <AlertCircle className="h-5 w-5 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="text-sm font-semibold">Error loading presence</p>
              <p className="text-xs opacity-90">{error}</p>
            </div>
            <Button size="sm" variant="outline" onClick={() => void load()} className="bg-transparent border-destructive/30 hover:bg-destructive/20 text-destructive">Retry</Button>
          </div>
        )}

        {loading && users.length === 0 && !error && (
          <div className="space-y-3 flex-1">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="flex items-center gap-3">
                <Skeleton className="h-8 w-8 rounded-full" />
                <div className="space-y-1">
                  <Skeleton className="h-3 w-24" />
                  <Skeleton className="h-2 w-16" />
                </div>
              </div>
            ))}
          </div>
        )}

        {!error && !(loading && users.length === 0) && (
          <div className="space-y-3 flex-1 overflow-y-auto pr-2">
            {online.length === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-4">No users online right now.</p>
            ) : (
              online.map((u: any, idx: number) => (
                <div key={idx} className="flex items-center gap-3 group">
                  <Avatar className="h-8 w-8 border border-primary/20 bg-primary/10">
                    <AvatarFallback className="text-primary font-medium text-xs">
                      {String(u.displayName || u.username || '?').trim().charAt(0).toUpperCase()}
                    </AvatarFallback>
                  </Avatar>
                  <div className="flex flex-col min-w-0">
                    <span className="text-sm font-medium truncate text-foreground group-hover:text-primary transition-colors">
                      {u.displayName || u.username || 'Unknown'}
                    </span>
                    <span className="text-xs text-muted-foreground truncate">
                      @{u.username || 'unknown'} • {u.redId || 'N/A'}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {count > online.length && (
          <div className="mt-4 pt-3 border-t border-border/50 text-center">
            <span className="text-xs font-medium text-muted-foreground">
              +{count - online.length} more users connected
            </span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
