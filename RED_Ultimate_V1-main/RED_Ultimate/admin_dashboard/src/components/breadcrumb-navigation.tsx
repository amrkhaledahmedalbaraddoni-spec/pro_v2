/**
 * breadcrumb-navigation.tsx
 * ًں§µ ظ…ط³ط§ط± ط§ظ„ظ…ظ„ط§ط­ط© (Breadcrumb)
 *
 * ظٹط¹ط±ط¶ ظ…ط³ط§ط± ط§ظ„طھظ†ظ‚ظ„ ط§ظ„ظ‡ط±ظ…ظٹ ظ…ظ† ط§ظ„طµظپط­ط© ط§ظ„ط­ط§ظ„ظٹط©
 * - ظٹط¯ط¹ظ… ط§ظ„ط¹ط±ط¨ظٹط© (RTL)
 * - ظٹط¹ط±ط¶ ط£ظٹظ‚ظˆظ†ط§طھ ظ„ظƒظ„ ظ…ط³طھظˆظ‰
 * - ظٹط³ظ…ط­ ط¨ط§ظ„طھظ†ظ‚ظ„ ط§ظ„ط³ط±ظٹط¹
 * - ظٹط¹ط±ط¶ ط¢ط®ط± 3 طµظپط­ط§طھ ظ…ظپطھظˆط­ط©
 */

'use client';

import { useEffect, useState } from 'react';
import { ChevronLeft, Home } from 'lucide-react';
import { cn } from '@/utils/cn';
import { findNavItem, findNavCategory, type PageKey } from '@/lib/navigation-structure';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';

interface BreadcrumbItem {
  key: PageKey;
  label: string;
  icon?: React.ReactNode;
}

interface BreadcrumbNavigationProps {
  currentPage: PageKey;
  onNavigate: (key: PageKey) => void;
  className?: string;
  recentPages?: PageKey[];
}

export function BreadcrumbNavigation({
  currentPage,
  onNavigate,
  className,
  recentPages = [],
}: BreadcrumbNavigationProps) {
  const [breadcrumbs, setBreadcrumbs] = useState<BreadcrumbItem[]>([]);

  useEffect(() => {
    const items: BreadcrumbItem[] = [];

    // ط£ط¶ظپ ط§ظ„ط±ط¦ظٹط³ظٹط© ط¯ط§ط¦ظ…ط§ظ‹
    items.push({
      key: 'dashboard',
      label: 'ظ„ظˆط­ط© ط§ظ„طھط­ظƒظ…',
      icon: <Home className="h-4 w-4" />,
    });

    // ط§ط¨ط­ط« ط¹ظ† ط§ظ„ظپط¦ط© ط§ظ„ط­ط§ظ„ظٹط©
    const category = findNavCategory(currentPage);
    if (category && currentPage !== 'dashboard') {
      items.push({
        key: currentPage,
        label: category.label,
      });
    }

    // ط£ط¶ظپ ط§ظ„طµظپط­ط© ط§ظ„ط­ط§ظ„ظٹط©
    const navItem = findNavItem(currentPage);
    if (navItem && currentPage !== 'dashboard') {
      items.push({
        key: currentPage,
        label: navItem.label,
        icon: navItem.icon ? <navItem.icon className="h-4 w-4" /> : undefined,
      });
    }

    setBreadcrumbs(items);
  }, [currentPage]);

  return (
    <nav
      aria-label="Breadcrumb"
      className={cn(
        'flex items-center gap-1 text-sm overflow-x-auto',
        className
      )}
    >
      {breadcrumbs.map((item, index) => {
        const isLast = index === breadcrumbs.length - 1;
        const isClickable = !isLast;

        return (
          <div key={`${item.key}-${index}`} className="flex items-center gap-1 shrink-0">
            {index > 0 && (
              <ChevronLeft
                className="h-4 w-4 text-muted-foreground"
                aria-hidden="true"
              />
            )}

            <Tooltip>
              <TooltipTrigger asChild>
                <button
                onClick={() => isClickable && onNavigate(item.key)}
                disabled={!isClickable}
                className={cn(
                  'inline-flex items-center gap-1.5 px-2 py-1 rounded transition-colors',
                  'text-sm font-medium whitespace-nowrap',
                  isClickable
                    ? 'hover:bg-accent hover:text-accent-foreground cursor-pointer'
                    : 'text-foreground cursor-default',
                  isLast && 'font-semibold text-foreground'
                )}
                aria-current={isLast ? 'page' : undefined}
              >
                {item.icon && <span className="shrink-0">{item.icon}</span>}
                <span>{item.label}</span>
                </button>
              </TooltipTrigger>
              {item.icon && <TooltipContent>{item.label}</TooltipContent>}
            </Tooltip>
          </div>
        );
      })}

      {/* ط¹ط±ط¶ ط§ظ„طµظپط­ط§طھ ط§ظ„ط­ط¯ظٹط«ط© ظپظٹ dropdown طµط؛ظٹط± */}
      {recentPages.length > 0 && (
        <div className="mr-auto flex items-center gap-1 border-l border-border pl-1">
          <span className="text-xs text-muted-foreground px-2">ط­ط¯ظٹط«:</span>
          {recentPages.slice(0, 2).map((pageKey) => {
            const item = findNavItem(pageKey);
            if (!item) return null;
            return (
              <Tooltip key={pageKey}>
                <TooltipTrigger asChild>
                  <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => onNavigate(pageKey)}
                  className="h-8 w-8 p-0 text-muted-foreground hover:text-foreground"
                  aria-label={item.label}
                >
                  {item.icon && <item.icon className="h-4 w-4" />}
                  </Button>
                </TooltipTrigger>
                <TooltipContent>{item.label}</TooltipContent>
              </Tooltip>
            );
          })}
        </div>
      )}
    </nav>
  );
}

export default BreadcrumbNavigation;
