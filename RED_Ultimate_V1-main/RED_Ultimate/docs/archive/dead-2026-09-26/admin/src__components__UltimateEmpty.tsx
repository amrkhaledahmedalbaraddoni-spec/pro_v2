import React from 'react';
import { Inbox, Search, Users, MessageSquare, Phone, Shield } from 'lucide-react';
import { Button } from './ui/button';

type Variant = 'default' | 'search' | 'contacts' | 'messages' | 'calls' | 'security';

const CONFIG: Record<Variant, { icon: React.ElementType; title: string; desc: string }> = {
  default: { icon: Inbox, title: 'No Data Found', desc: 'The list is completely empty at this time' },
  search: { icon: Search, title: 'No Results', desc: 'Try adjusting your search criteria' },
  contacts: { icon: Users, title: 'No Contacts', desc: 'Add some contacts to get started' },
  messages: { icon: MessageSquare, title: 'No Messages', desc: 'Start a conversation' },
  calls: { icon: Phone, title: 'No Calls', desc: 'Your call history will appear here' },
  security: { icon: Shield, title: 'Secure', desc: 'No security threats detected' },
};

export default function UltimateEmpty({ 
  variant = 'default', 
  actionText, 
  onAction, 
  description 
}: { 
  variant?: Variant; 
  actionText?: string; 
  onAction?: () => void; 
  description?: string;
}) {
  const c = CONFIG[variant];
  const Icon = c.icon;
  
  return (
    <div className="flex flex-col items-center justify-center p-12 text-center animate-slide-up">
      <div className="flex h-20 w-20 items-center justify-center rounded-full bg-secondary/50 mb-4 ring-8 ring-secondary/20">
        <Icon className="h-10 w-10 text-muted-foreground opacity-80" strokeWidth={1.5} />
      </div>
      <h3 className="text-lg font-semibold text-foreground mb-1">{c.title}</h3>
      <p className="text-sm text-muted-foreground max-w-sm mb-6">
        {description || c.desc}
      </p>
      {actionText && onAction && (
        <Button onClick={onAction} variant="default">
          {actionText}
        </Button>
      )}
    </div>
  );
}
