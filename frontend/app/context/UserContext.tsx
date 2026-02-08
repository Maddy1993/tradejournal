"use client";

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';

interface BrokerAccount {
    id: string;
    name: string;
    number: string;
    institutionName: string;
    lastSync?: Date;
}

interface UserContextType {
    userId: string | null;
    userSecret: string | null;
    email: string | null;
    connectedBrokers: BrokerAccount[];
    isLoading: boolean;
    registerUser: (email: string) => Promise<void>;
    connectBroker: () => Promise<string>;
    syncBrokers: () => Promise<void>;
    fetchBrokers: () => Promise<void>;
    logout: () => void;
}

const UserContext = createContext<UserContextType | undefined>(undefined);

export function UserProvider({ children }: { children: ReactNode }) {
    const [userId, setUserId] = useState<string | null>(null);
    const [userSecret, setUserSecret] = useState<string | null>(null);
    const [email, setEmail] = useState<string | null>(null);
    const [connectedBrokers, setConnectedBrokers] = useState<BrokerAccount[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    // Load user data from localStorage on mount
    useEffect(() => {
        const storedUserId = localStorage.getItem('userId');
        const storedUserSecret = localStorage.getItem('userSecret');
        const storedEmail = localStorage.getItem('email');

        if (storedUserId && storedUserSecret && storedEmail) {
            setUserId(storedUserId);
            setUserSecret(storedUserSecret);
            setEmail(storedEmail);
            // Fetch brokers for existing user
            fetchBrokersInternal(storedUserId, storedUserSecret);
        } else {
            setIsLoading(false);
        }
    }, []);

    const registerUser = async (email: string) => {
        try {
            setIsLoading(true);
            // First, try to fetch existing user secret (acts as login)
            const loginResponse = await fetch(`http://localhost:8080/api/users/${email}/secret`);

            if (loginResponse.ok) {
                // User exists, retrieve their secret
                const data = await loginResponse.json();
                const secret = data.userSecret;

                // Store credentials
                localStorage.setItem('userId', email);
                localStorage.setItem('userSecret', secret);
                localStorage.setItem('email', email);

                setUserId(email);
                setUserSecret(secret);
                setEmail(email);

                // Fetch brokers for existing user
                await fetchBrokersInternal(email, secret);
                return; // Exit after successful login
            }

            // User doesn't exist (404), proceed with registration
            if (loginResponse.status === 404) {
                const response = await fetch('http://localhost:8080/api/brokerage/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ userId: email }),
                });

                if (!response.ok) {
                    throw new Error('Registration failed');
                }

                const data = await response.json();
                const secret = data.userSecret;

                // Store credentials locally
                localStorage.setItem('userId', email);
                localStorage.setItem('userSecret', secret);
                localStorage.setItem('email', email);

                setUserId(email);
                setUserSecret(secret);
                setEmail(email);
            } else {
                throw new Error('Unexpected error during login/registration');
            }
        } catch (error) {
            console.error('Error during registration/login:', error);
            throw error;
        } finally {
            setIsLoading(false);
        }
    };

    const connectBroker = async (): Promise<string> => {
        if (!userId || !userSecret) throw new Error('User not registered');

        const response = await fetch('http://localhost:8080/api/brokerage/connect', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId, userSecret }),
        });

        if (!response.ok) throw new Error('Failed to generate connection link');

        const data = await response.json();
        return data.redirectURI || data.loginRedirectURI;
    };

    const syncBrokers = async () => {
        if (!userId) throw new Error('User not registered');

        setIsLoading(true);
        try {
            const response = await fetch('http://localhost:8080/api/brokerage/sync', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId }), // Only send userId (email)
            });

            if (!response.ok) throw new Error('Sync failed');

            // Refresh broker list after sync
            if (userSecret) {
                await fetchBrokersInternal(userId, userSecret);
            }
        } catch (error) {
            console.error('Sync error:', error);
            throw error;
        } finally {
            setIsLoading(false);
        }
    };

    const fetchBrokersInternal = async (uid: string, secret: string) => {
        try {
            const response = await fetch(`http://localhost:8080/api/brokerage/accounts?userId=${uid}&userSecret=${secret}`);

            if (!response.ok) {
                setConnectedBrokers([]);
                return;
            }

            const accounts = await response.json();
            setConnectedBrokers(accounts.map((acc: any) => ({
                id: acc.id,
                name: acc.name || 'Account',
                number: acc.number,
                institutionName: acc.institution_name || 'Unknown',
                lastSync: acc.syncDate ? new Date(acc.syncDate) : undefined,
            })));
        } catch (error) {
            console.error('Failed to fetch brokers:', error);
            setConnectedBrokers([]);
        } finally {
            setIsLoading(false);
        }
    };

    const fetchBrokers = async () => {
        if (userId && userSecret) {
            await fetchBrokersInternal(userId, userSecret);
        }
    };

    const logout = () => {
        // Clear all user data from localStorage
        localStorage.removeItem('userId');
        localStorage.removeItem('userSecret');
        localStorage.removeItem('email');

        // Reset all state
        setUserId(null);
        setUserSecret(null);
        setEmail(null);
        setConnectedBrokers([]);
        setIsLoading(false);
    };

    return (
        <UserContext.Provider
            value={{
                userId,
                userSecret,
                email,
                connectedBrokers,
                isLoading,
                registerUser,
                connectBroker,
                syncBrokers,
                fetchBrokers,
                logout,
            }}
        >
            {children}
        </UserContext.Provider>
    );
}

export function useUser() {
    const context = useContext(UserContext);
    if (context === undefined) {
        throw new Error('useUser must be used within a UserProvider');
    }
    return context;
}
