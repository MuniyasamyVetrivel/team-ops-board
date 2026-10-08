import ReactMarkdown from 'react-markdown';

import { cn } from '@/lib/utils';

/**
 * Renders article Markdown. Raw HTML in the source is not rendered (react-markdown's default), so user content
 * cannot inject markup or scripts. Links open in a new tab without access to this window.
 */
export function Markdown({ children, className }: { children: string; className?: string }) {
  return (
    <div className={cn('markdown', className)}>
      <ReactMarkdown
        components={{
          a: ({ href, children: text }) => (
            <a href={href} target="_blank" rel="noopener noreferrer">
              {text}
            </a>
          ),
        }}
      >
        {children}
      </ReactMarkdown>
    </div>
  );
}
