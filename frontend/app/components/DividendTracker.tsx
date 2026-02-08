"use client";

import React, { useEffect, useState } from 'react';

interface Dividend {
    id: string;
    symbol: string;
    amount: number;
    exDate: string;
    payDate: string;
    type: string;
}

const DividendTracker: React.FC = () => {
    const [dividends, setDividends] = useState<Dividend[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // Hardcoded user ID for now
    const userId = "123e4567-e89b-12d3-a456-426614174000";

    useEffect(() => {
        const fetchDividends = async () => {
            try {
                // Placeholder - endpoint not yet implemented
                // const response = await fetch(`http://localhost:8080/api/dividends?userId=${userId}`);
                // const data = await response.json();
                // setDividends(data);

                // Mock data for now
                setDividends([]);
                setLoading(false);
            } catch (err) {
                setError(err instanceof Error ? err.message : 'An error occurred');
                setLoading(false);
            }
        };

        fetchDividends();
    }, [userId]);

    const formatCurrency = (value: number) => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
        }).format(value);
    };

    const totalDividends = dividends.reduce((sum, d) => sum + d.amount, 0);

    if (loading) return <div className="p-4 text-center">Loading dividends...</div>;
    if (error) return <div className="p-4 text-center text-red-500">Error: {error}</div>;

    return (
        <div className="bg-gray-800 rounded-lg shadow-lg p-6">
            <div className="flex items-center justify-between mb-6">
                <h2 className="text-2xl font-semibold">Dividend Tracker</h2>
                <div className="text-right">
                    <p className="text-sm text-gray-400">Total Dividends (YTD)</p>
                    <p className="text-2xl font-bold text-green-400">{formatCurrency(totalDividends)}</p>
                </div>
            </div>

            {dividends.length === 0 ? (
                <div className="text-center py-8 text-gray-400">
                    <svg className="mx-auto h-12 w-12 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <p>No dividends recorded yet</p>
                    <p className="text-sm mt-2">Sync your broker to import dividend data</p>
                </div>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead className="text-xs uppercase bg-gray-700 text-gray-400">
                            <tr>
                                <th scope="col" className="px-6 py-3">Symbol</th>
                                <th scope="col" className="px-6 py-3">Amount</th>
                                <th scope="col" className="px-6 py-3">Type</th>
                                <th scope="col" className="px-6 py-3">Ex-Date</th>
                                <th scope="col" className="px-6 py-3">Pay Date</th>
                            </tr>
                        </thead>
                        <tbody>
                            {dividends.map((dividend) => (
                                <tr key={dividend.id} className="border-b bg-gray-800 border-gray-700 hover:bg-gray-600">
                                    <td className="px-6 py-4 font-medium text-white whitespace-nowrap">
                                        {dividend.symbol}
                                    </td>
                                    <td className="px-6 py-4 text-green-400">
                                        {formatCurrency(dividend.amount)}
                                    </td>
                                    <td className="px-6 py-4">
                                        {dividend.type}
                                    </td>
                                    <td className="px-6 py-4">
                                        {new Date(dividend.exDate).toLocaleDateString()}
                                    </td>
                                    <td className="px-6 py-4">
                                        {new Date(dividend.payDate).toLocaleDateString()}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default DividendTracker;
