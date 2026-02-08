"use client";

import React, { useEffect, useState } from 'react';

interface Holding {
    id: string;
    symbol: string;
    quantity: number;
    averageCost: number;
    currentPrice: number;
    marketValue: number;
}

interface Dividend {
    id: string;
    symbol: string;
    amount: number;
    payDate: string;
    exDate: string | null;
}

interface PerformanceData {
    totalEquity?: number;
    totalAbsoluteReturn?: number;
    totalPercentReturn?: number;
}

interface DashboardData {
    totalEquity: number;
    unrealizedPL: number;
    performance?: {
        totalGainLoss?: number;
    };
}

const Dashboard: React.FC = () => {
    const [dashboardData, setDashboardData] = useState<DashboardData | null>(null);
    const [holdings, setHoldings] = useState<Holding[]>([]);
    const [dividends, setDividends] = useState<Dividend[]>([]);
    const [performance, setPerformance] = useState<PerformanceData | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // Hardcoded user ID for now - replace with actual auth
    const userId = "123e4567-e89b-12d3-a456-426614174000";
    const userEmail = "trader@mail.com";

    useEffect(() => {
        const fetchData = async () => {
            try {
                // Fetch all data in parallel
                const [dashRes, holdingsRes, dividendsRes, perfRes] = await Promise.all([
                    fetch(`http://localhost:8080/api/dashboard?userId=${userId}`).catch(() => null),
                    fetch(`http://localhost:8080/api/holdings?userId=${userId}`).catch(() => null),
                    fetch(`http://localhost:8080/api/dividends?userId=${userId}`).catch(() => null),
                    fetch(`http://localhost:8080/api/performance?userId=${userEmail}&startDate=2024-01-01`).catch(() => null)
                ]);

                // Parse responses
                if (dashRes?.ok) {
                    const data = await dashRes.json();
                    setDashboardData(data);
                }

                if (holdingsRes?.ok) {
                    const data = await holdingsRes.json();
                    setHold

                    ings(Array.isArray(data) ? data : (data.holdings || []));
                }

                if (dividendsRes?.ok) {
                    const data = await dividendsRes.json();
                    setDividends(Array.isArray(data) ? data : []);
                }

                if (perfRes?.ok) {
                    const data = await perfRes.json();
                    setPerformance(data);
                }

            } catch (err) {
                setError(err instanceof Error ? err.message : 'An error occurred');
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [userId, userEmail]);

    if (loading) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <div className="text-center">
                    <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mb-4"></div>
                    <p className="text-gray-600">Loading portfolio data...</p>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <div className="bg-red-50 border border-red-200 rounded-lg p-6 max-w-md">
                    <h3 className="text-red-800 font-semibold mb-2">Error loading dashboard</h3>
                    <p className="text-red-600">{error}</p>
                    <button
                        onClick={() => window.location.reload()}
                        className="mt-4 bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700"
                    >
                        Retry
                    </button>
                </div>
            </div>
        );
    }

    const formatCurrency = (value: number) => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
        }).format(value);
    };

    const formatPercentage = (value: number, total: number) => {
        if (total === 0) return '0.00%';
        return ((value / total) * 100).toFixed(2) + '%';
    };

    const totalDividends = dividends.reduce((sum, div) => sum + div.amount, 0);
    const totalEquity = dashboardData?.totalEquity || holdings.reduce((sum, h) => sum + h.marketValue, 0);
    const unrealizedPL = dashboardData?.unrealizedPL || 0;

    return (
        <div className="space-y-6">
            {/* Summary Cards */}
            <div className="grid gap-6 md:grid-cols-4">
                {/* Total Equity Card */}
                <div className="bg-gradient-to-br from-blue-500 to-blue-700 rounded-lg shadow-lg p-6 text-white">
                    <h3 className="text-sm font-medium opacity-90">Total Equity</h3>
                    <p className="text-3xl font-bold mt-2">{formatCurrency(totalEquity)}</p>
                    <p className="text-xs mt-2 opacity-75">Current portfolio value</p>
                </div>

                {/* Unrealized P&L Card */}
                <div className={`rounded-lg shadow-lg p-6 text-white ${unrealizedPL >= 0
                    ? 'bg-gradient-to-br from-green-500 to-green-700'
                    : 'bg-gradient-to-br from-red-500 to-red-700'
                    }`}>
                    <h3 className="text-sm font-medium opacity-90">Unrealized P&L</h3>
                    <p className="text-3xl font-bold mt-2">{formatCurrency(unrealizedPL)}</p>
                    <p className="text-xs mt-2 opacity-75">
                        {unrealizedPL >= 0 ? '↑' : '↓'} {formatPercentage(unrealizedPL, totalEquity - unrealizedPL)}
                    </p>
                </div>

                {/* Total Return Card */}
                <div className="bg-gradient-to-br from-purple-500 to-purple-700 rounded-lg shadow-lg p-6 text-white">
                    <h3 className="text-sm font-medium opacity-90">Total Return</h3>
                    <p className="text-3xl font-bold mt-2">
                        {performance?.totalAbsoluteReturn !== undefined
                            ? formatCurrency(performance.totalAbsoluteReturn)
                            : 'N/A'}
                    </p>
                    <p className="text-xs mt-2 opacity-75">
                        {performance?.totalPercentReturn !== undefined
                            ? `${performance.totalPercentReturn.toFixed(2)}%`
                            : 'Year to date'}
                    </p>
                </div>

                {/* Dividends Card */}
                <div className="bg-gradient-to-br from-amber-500 to-amber-700 rounded-lg shadow-lg p-6 text-white">
                    <h3 className="text-sm font-medium opacity-90">Total Dividends</h3>
                    <p className="text-3xl font-bold mt-2">{formatCurrency(totalDividends)}</p>
                    <p className="text-xs mt-2 opacity-75">{dividends.length} payments tracked</p>
                </div>
            </div>

            {/* Holdings Table */}
            <div className="bg-white rounded-lg shadow-lg overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-200">
                    <h2 className="text-xl font-semibold text-gray-800">Holdings</h2>
                </div>
                <div className="overflow-x-auto">
                    {holdings.length > 0 ? (
                        <table className="w-full">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Symbol</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Quantity</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Avg Cost</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Current Price</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Market Value</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Gain/Loss</th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {holdings.map((holding) => {
                                    const gainLoss = holding.marketValue - (holding.averageCost * holding.quantity);
                                    const gainLossPct = holding.averageCost > 0
                                        ? ((holding.currentPrice - holding.averageCost) / holding.averageCost) * 100
                                        : 0;
                                    return (
                                        <tr key={holding.id} className="hover:bg-gray-50">
                                            <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{holding.symbol}</td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-500">{holding.quantity.toFixed(2)}</td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-500">{formatCurrency(holding.averageCost)}</td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-500">{formatCurrency(holding.currentPrice)}</td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right font-medium text-gray-900">{formatCurrency(holding.marketValue)}</td>
                                            <td className={`px-6 py-4 whitespace-nowrap text-sm text-right font-medium ${gainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                                                {formatCurrency(gainLoss)} ({gainLossPct >= 0 ? '+' : ''}{gainLossPct.toFixed(2)}%)
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    ) : (
                        <div className="px-6 py-12 text-center text-gray-500">
                            <p>No holdings found. Connect your broker account to see your portfolio.</p>
                        </div>
                    )}
                </div>
            </div>

            {/* Dividends Table */}
            <div className="bg-white rounded-lg shadow-lg overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-200">
                    <h2 className="text-xl font-semibold text-gray-800">Recent Dividends</h2>
                </div>
                <div className="overflow-x-auto">
                    {dividends.length > 0 ? (
                        <table className="w-full">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Symbol</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Amount</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Pay Date</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Ex Date</th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {dividends.slice(0, 10).map((dividend) => (
                                    <tr key={dividend.id} className="hover:bg-gray-50">
                                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{dividend.symbol}</td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-green-600 font-medium">{formatCurrency(dividend.amount)}</td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-500">{new Date(dividend.payDate).toLocaleDateString()}</td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-500">
                                            {dividend.exDate ? new Date(dividend.exDate).toLocaleDateString() : 'N/A'}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    ) : (
                        <div className="px-6 py-12 text-center text-gray-500">
                            <p>No dividends tracked yet. Sync your dividend history to see payments.</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default Dashboard;
