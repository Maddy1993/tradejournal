"use client";

import Header from "../components/Header";
import TradeHistory from "../components/TradeHistory";
import { useUser } from '../context/UserContext';
import EmailRegistrationModal from '../components/EmailRegistrationModal';

export default function TradesPage() {
    const { email, registerUser } = useUser();

    return (
        <>
            {!email && <EmailRegistrationModal isOpen={!email} onRegister={registerUser} />}
            <Header />
            <main className="min-h-screen bg-gray-50">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                    <TradeHistory />
                </div>
            </main>
        </>
    );
}
