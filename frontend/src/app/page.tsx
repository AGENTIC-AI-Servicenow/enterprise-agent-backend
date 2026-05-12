import { redirect } from 'next/navigation';

export default function HomePage() {
  // Redirect to dashboard (protected route will handle auth)
  redirect('/dashboard');
}
