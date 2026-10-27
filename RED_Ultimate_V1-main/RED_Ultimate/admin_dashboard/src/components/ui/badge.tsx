'use client';

import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/utils/cn';

const badgeVariants = cva(
  'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2',
  {
    variants: {
      variant: {
        default: 'border-primary/30 bg-primary/10 text-primary hover:bg-primary/20 backdrop-blur-sm',
        secondary: 'border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80',
        destructive: 'border-destructive/30 bg-destructive/10 text-destructive hover:bg-destructive/20 backdrop-blur-sm',
        outline: 'text-foreground',
        success: 'border-green-500/30 bg-green-500/10 text-green-500 hover:bg-green-500/20 backdrop-blur-sm',
        warning: 'border-yellow-500/30 bg-yellow-500/10 text-yellow-500 hover:bg-yellow-500/20 backdrop-blur-sm',
        info: 'border-blue-500/30 bg-blue-500/10 text-blue-500 hover:bg-blue-500/20 backdrop-blur-sm',
        // ✅ FIX 2026-09-22: أسماء comfort-API المستخدمة في الصفحات (blue/gold/green/danger)
        blue: 'border-blue-500/30 bg-blue-500/10 text-blue-500 hover:bg-blue-500/20 backdrop-blur-sm',
        gold: 'border-amber-500/30 bg-amber-500/10 text-amber-500 hover:bg-amber-500/20 backdrop-blur-sm',
        green: 'border-green-500/30 bg-green-500/10 text-green-500 hover:bg-green-500/20 backdrop-blur-sm',
        danger: 'border-destructive/30 bg-destructive/10 text-destructive hover:bg-destructive/20 backdrop-blur-sm',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  }
);

// ✅ FIX 2026-09-22: HTMLSpanAttributes أُزيل من @types/react (React 19) → HTMLAttributes
export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, children, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props}>{children}</span>;
}

export { Badge, badgeVariants };