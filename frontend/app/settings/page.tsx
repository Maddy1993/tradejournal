"use client";

import React, { useState } from 'react';
import { useUser } from '../context/UserContext';
import Header from '../components/Header';
import EmailRegistrationModal from '../components/EmailRegistrationModal';

export default function SettingsPage() {
    const { email, userId, connectedBrokers, connectBroker, syncBrokers, registerUser, isLoading } = useUser();
    const [showConnectModal, setShowConnectModal] = useState(false);
    const [syncing, setSyncing] = useState(false);
    const [showEmailModal, setShowEmailModal] = useState(!email);

    const handleRegister = async (email: string) => {
        await registerUser(email);
        setShowEmailModal(false);
    };

    const handleConnectBroker = async () => {
        try {
            const redirectUrl = await connectBroker();
            // Open SnapTrade OAuth in new window
            const width = 600;
            const height = 700;
            const left = window.screenX + (window.outerWidth - width) / 2;
            const top = window.screenY + (window.outerHeight - height) / 2;

            const popup = window.open(
                redirectUrl,
                'SnapTrade Connection',
                `width=${width},height=${height},left=${left},top=${top}`
            );

            // Poll for popup closure and auto-sync
            const checkPopup = setInterval(async () => {
                if (popup && popup.closed) {
                    clearInterval(checkPopup);
                    // Auto-sync after connection
                    try {
                        setSyncing(true);
                        await syncBrokers();
                    } catch (error) {
                        console.error('Auto-sync failed:', error);
                        alert('Connection successful, but auto-sync failed. Please sync manually.');
                    } finally {
                        setSyncing(false);
                    }
                }
            }, 500);
        } catch (error) {
            console.error('Connection error:', error);
            alert('Failed to generate connection link. Please try again.');
        }
    };

    if (!email) {
        return <EmailRegistrationModal isOpen={showEmailModal} onRegister={handleRegister} />;
    }

    return (
        <>
            <Header />
            <main className="min-h-screen bg-gray-900 text-white p-8">
                <div className="max-w-4xl mx-auto">
                    <h1 className="text-3xl font-bold mb-8">Settings</h1>

                    {/* User Info Card */}
                    <div className="bg-gray-800 rounded-lg p-6 mb-6 border border-gray-700">
                        <h2 className="text-xl font-semibold mb-4">Account Information</h2>
                        <div className="space-y-3">
                            <div>
                                <label className="text-sm text-gray-400">Email</label>
                                <p className="text-white">{email}</p>
                            </div>
                            <div>
                                <label className="text-sm text-gray-400">User ID</label>
                                <p className="text-white font-mono text-sm">{userId}</p>
                            </div>
                        </div>
                    </div>

                    {/* Connected Brokers Card */}
                    <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-xl font-semibold">Connected Brokers</h2>
                            <button
                                onClick={handleConnectBroker}
                                className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
                            >
                                + Connect Broker
                            </button>
                        </div>

                        {isLoading ? (
                            <p className="text-gray-400 text-center py-4">Loading...</p>
                        ) : connectedBrokers.length === 0 ? (
                            <div className="text-center py-8 text-gray-400">
                                <svg className="mx-auto h-12 w-12 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                                </svg>
                                <p>No brokers connected yet</p>
                                <p className="text-sm mt-2">Click "Connect Broker" to get started</p>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {connectedBrokers.map((broker) => (
                                    <div key={broker.id} className="flex items-center justify-between p-4 bg-gray-700 rounded-lg">
                                        <div>
                                            <p className="font-medium">{broker.institutionName}</p>
                                            <p className="text-sm text-gray-400">Account: {broker.number}</p>
                                            {broker.lastSync && (
                                                <p className="text-xs text-gray-500 mt-1">
                                                    Last synced: {broker.lastSync.toLocaleString()}
                                                </p>
                                            )}
                                        </div>
                                        <div className="flex items-center space-x-3">
                                            <span className="flex items-center text-sm text-green-400">
                                                <span className="w-2 h-2 bg-green-400 rounded-full mr-2"></span>
                                                Connected
                                            </span>
                                            <button
                                                onClick={() => {
                                                    if (confirm(`Remove ${broker.institutionName}?`)) {
                                                        // TODO: Implement delete API call
                                                        alert('Delete broker functionality coming soon!');
                                                    }
                                                }}
                                                className="p-2 text-gray-400 hover:text-red-400 hover:bg-gray-600 rounded transition-colors"
                                                title="Remove broker"
                                            >
                                                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                                </svg>
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}

                        {syncing && (
                            <div className="mt-4 p-3 bg-blue-900/50 border border-blue-500 rounded-lg text-blue-200 text-sm text-center">
                                Syncing broker data...
                            </div>
                        )}
                    </div>
                </div>
            </main>
        </>
    );
}
