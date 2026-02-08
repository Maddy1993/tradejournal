"use client";

import { useUser } from './context/UserContext';
import Header from "./components/Header";
import Dashboard from "./components/Dashboard";
import EmailRegistrationModal from './components/EmailRegistrationModal';

export default function Home() {
  const { email, registerUser } = useUser();

  return (
    <>
      {!email && <EmailRegistrationModal isOpen={!email} onRegister={registerUser} />}

      <Header />
      <main className="min-h-screen bg-gray-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="mb-8">
            <h1 className="text-4xl font-bold text-gray-900">Trade Journal</h1>
            <p className="mt-2 text-gray-600">Track your portfolio, performance, and dividends</p>
          </div>

          {/* Comprehensive Dashboard with Summary Cards, Holdings, and Dividends */}
          <Dashboard />
        </div>
      </main>
    </>
  );
}
