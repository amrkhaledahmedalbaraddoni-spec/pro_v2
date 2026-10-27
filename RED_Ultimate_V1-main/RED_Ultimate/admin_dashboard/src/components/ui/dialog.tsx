import { cn } from '@/utils';
import { useCallback, useEffect, useRef, cloneElement, type ReactNode, type ReactElement } from 'react';

interface DialogProps {
  open: boolean;
  /** Legacy/custom close handler (security/system pages). */
  onClose?: () => void;
  /** Radix-style controlled handler (Content/Groups pages). Either onClose or onOpenChange is required. */
  onOpenChange?: (open: boolean) => void;
  /** Legacy header title. Omit when rendering a custom <DialogHeader> (Radix-style). */
  title?: string;
  children: ReactNode;
  className?: string;
  size?: 'sm' | 'md' | 'lg' | 'xl' | 'full';
}

const sizes = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
  full: 'max-w-4xl',
};

function DialogBase({ open, onClose, onOpenChange, title, children, className, size = 'md' }: DialogProps) {
  const overlayRef = useRef<HTMLDivElement>(null);
  const handleClose = useCallback(() => {
    onClose?.();
    onOpenChange?.(false);
  }, [onClose, onOpenChange]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') handleClose();
    };
    if (open) {
      document.addEventListener('keydown', handleKeyDown);
      document.body.style.overflow = 'hidden';
    }
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = '';
    };
  }, [open, handleClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div ref={overlayRef} className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={handleClose} aria-hidden="true" />
      <div
        className={cn('relative yn-card yn-card-liquid yn-glass w-full shadow-xl', sizes[size], className)}
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
      >
        {title != null && title !== '' && (
          <div className="flex items-center justify-between p-4 border-b border-yn-border">
            <h2 id="dialog-title" className="font-heading text-lg font-semibold text-yn-text">{title}</h2>
            <button onClick={handleClose} className="p-1 rounded-lg text-yn-text-secondary hover:text-yn-text hover:bg-yn-surface transition-colors" aria-label="Close dialog">✕</button>
          </div>
        )}
        <div className="p-4 max-h-[70vh] overflow-y-auto">{children}</div>
      </div>
    </div>
  );
}

export function DialogTrigger({ children, onOpenChange }: { children: ReactElement; onOpenChange: (open: boolean) => void }) {
  return cloneElement(children, { onClick: () => onOpenChange(true) } as Record<string, unknown>);
}

export function DialogContent({ children, className, dir }: { children: ReactNode; className?: string; dir?: 'ltr' | 'rtl' | 'auto' }) {
  return <div className={className} dir={dir}>{children}</div>;
}

export function DialogHeader({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn('mb-4', className)}>{children}</div>;
}

export function DialogTitle({ children, className }: { children: ReactNode; className?: string }) {
  return <h3 className={cn('font-heading text-lg font-semibold text-yn-text', className)}>{children}</h3>;
}

export function DialogDescription({ children, className }: { children: ReactNode; className?: string }) {
  return <p className={cn('text-sm text-yn-text-secondary mt-1', className)}>{children}</p>;
}

export function DialogFooter({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn('flex items-center justify-end gap-2 mt-6 pt-4 border-t border-yn-border', className)}>{children}</div>;
}

export function DialogClose({ onClick, children }: { onClick: () => void; children?: ReactNode }) {
  return <button onClick={onClick} className="yn-btn yn-btn-ghost">{children}</button>;
}

export const Dialog = Object.assign(DialogBase, {
  Trigger: DialogTrigger,
  Content: DialogContent,
  Header: DialogHeader,
  Title: DialogTitle,
  Description: DialogDescription,
  Footer: DialogFooter,
  Close: DialogClose,
});
export default Dialog;
