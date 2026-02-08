"use client";

import React from 'react';
import Link from 'next/link';
import { useUser } from '../context/UserContext';
import { usePathname } from 'next/navigation';

const Header: React.FC = () => {
    const { email, connectedBrokers, syncBrokers, isLoading } = useUser();
    const pathname = usePathname();
    const [syncing, setSyncing] = React.useState(false);

    const handleSync = async () => {
        setSyncing(true);
        try {
            await syncBrokers();
        } catch (error) {
            console.error('Sync failed:', error);
            alert('Sync failed. Please try again.');
        } finally {
            setSyncing(false);
        }
    };

    const isActive = (path: string) => pathname === path;

    return (
        <header className="sticky top-0 z-50 bg-gradient-to-r from-gray-900 via-gray-800 to-gray-900 border-b border-gray-700 shadow-lg">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex items-center justify-between h-16">
                    {/* Logo */}
                    <div className="flex items-center">
                        <Link href="/" className="flex items-center space-x-2">
                            <div className="w-8 h-8 bg-gradient-to-br from-blue-500 to-purple-600 rounded-lg flex items-center justify-center">
                                <span className="text-white font-bold text-lg">TJ</span>
                            </div>
                            <span className="text-white font-semibold text-xl">Trade Journal</span>
                        </Link>
                    </div>

                    {/* Navigation */}
                    <nav className="hidden md:flex space-x-8">
                        <Link
                            href="/"
                            className={`px-3 py-2 rounded-md text-sm font-medium transition-colors ${isActive('/')
                                    ? 'text-white bg-gray-700'
                                    : 'text-gray-300 hover:text-white hover:bg-gray-700'
                                }`}
                        >
                            Dashboard
                        </Link>
                        <Link
                            href="/settings"
                            className={`px-3 py-2 rounded-md text-sm font-medium transition-colors ${isActive('/settings')
                                    ? 'text-white bg-gray-700'
                                    : 'text-gray-300 hover:text-white hover:bg-gray-700'
                                }`}
                        >
                            Settings
                        </Link>
                    </nav>

                    {/* Right side - User info and actions */}
                    <div className="flex items-center space-x-4">
                        {/* Connection status */}
                        <div className="hidden sm:flex items-center space-x-2">
                            {connectedBrokers.length > 0 ? (
                                <span className="flex items-center text-sm text-green-400">
                                    <span className="w-2 h-2 bg-green-400 rounded-full mr-2 animate-pulse"></span>
                                    {connectedBrokers.length} broker{connectedBrokers.length > 1 ? 's' : ''} connected
                                </span>
                            ) : (
                                <span className="flex items-center text-sm text-gray-400">
                                    <span className="w-2 h-2 bg-gray-400 rounded-full mr-2"></span>
                                    No brokers connected
                                </span>
                            )}
                        </div>

                        {/* Sync button */}
                        {connectedBrokers.length > 0 && (
                            <button
                                onClick={handleSync}
                                disabled={syncing || isLoading}
                                className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                            >
                                <svg
                                    className={`w-4 h-4 ${syncing ? 'animate-spin' : ''}`}
                                    fill="none"
                                    viewBox="0 0 24 24"
                                    stroke="currentColor"
                                >
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                                </svg>
                                <span className="hidden sm:inline">{syncing ? 'Syncing...' : 'Sync'}</span>
                            </button>
                        )}

                        {/* User email (if logged in) */}
                        {email && (
                            <div className="hidden lg:block text-sm text-gray-400">
                                {email}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </header>
    );
};

export default Header;
