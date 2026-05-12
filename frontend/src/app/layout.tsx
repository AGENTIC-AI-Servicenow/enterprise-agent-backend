import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import { Providers } from '@/components/providers';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'Enterprise AI Agent | ServiceNow Integration',
  description: 'AI-powered assistant for ServiceNow incident management and automation',
  keywords: ['ServiceNow', 'AI', 'Automation', 'ITSM', 'Enterprise'],
  authors: [{ name: 'Enterprise AI Team' }],
  robots: 'noindex, nofollow', // Internal tool
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={inter.className}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
