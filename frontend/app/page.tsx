"use client";

import { useUser } from './context/UserContext';
import Header from "./components/Header";
import Dashboard from "./components/Dashboard";
import DividendTracker from "./components/DividendTracker";
import TradeTable from "./components/TradeTable";
import EmailRegistrationModal from './components/EmailRegistrationModal';

export default function Home() {
  const { email, registerUser } = useUser();

  return (
    <>
      {!email && <EmailRegistrationModal isOpen={!email} onRegister={registerUser} />}

      <Header />
      <main className="flex min-h-screen flex-col items-center p-24 bg-gray-900 text-white">
        <h1 className="text-4xl font-bold mb-8">Trade Journal</h1>

        <div className="w-full max-w-7xl space-y-8">
          {/* Dashboard Summary */}
          <Dashboard />

          {/* Dividend Tracker */}
          <DividendTracker />

          {/* Holdings Table */}
          <div className="font-mono text-sm">
            <h2 className="text-2xl font-semibold mb-4">Holdings</h2>
            <TradeTable />
          </div>
        </div>
      </main>
    </>
  );
}
