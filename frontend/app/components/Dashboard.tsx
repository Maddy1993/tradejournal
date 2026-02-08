"use client";

import React, { useEffect, useState } from 'react';
import { useUser } from '../context/UserContext';

// ==================== INTERFACES ====================
interface Holding {
    id: string;
    symbol: string;
    quantity: number;
    averageCost: number;
    currentPrice: number;
    marketValue: number;
    accountId?: string;
}

interface Dividend {
    id: string;
    symbol: string;
    amount: number;
    payDate: string;
    exDate: string | null;
    type?: string;
}

interface PerformanceData {
    totalEquity?: number;
    totalAbsoluteReturn?: number;
    totalPercentReturn?: number;
    // SnapTrade performance object can contain many more fields
    [key: string]: any;
}

interface DashboardData {
    totalEquity: number;
    unrealizedPL: number;
    totalPositions?: number;
    performance?: {
        totalGainLoss?: number;
    };
}

// ==================== COMPONENT ====================
const Dashboard: React.FC = () => {
    // Get user from context
    const { email: userEmail, userId, connectedBrokers } = useUser();

    // State management
    const [dashboardData, setDashboardData] = useState<DashboardData | null>(null);
    const [holdings, setHoldings] = useState<Holding[]>([]);
    const [dividends, setDividends] = useState<Dividend[]>([]);
    const [performance, setPerformance] = useState<PerformanceData | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // ==================== DATA FETCHING ====================
    useEffect(() => {
        if (!userId || !userEmail) {
            setLoading(false);
            return;
        }

        const fetchData = async () => {
            setLoading(true);
            setError(null);

            try {
                // Calculate date range for performance (last 30 days by default)
                const endDate = new Date().toISOString().split('T')[0];
                const startDate = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
                    .toISOString()
                    .split('T')[0];

                // Fetch all endpoints in parallel for better performance
                const [dashRes, holdingsRes, dividendsRes, perfRes] = await Promise.allSettled([
                    fetch(`http://localhost:8080/api/dashboard?userId=${userId}`),
                    fetch(`http://localhost:8080/api/holdings?userId=${userEmail}`), // Use userEmail for consistency
                    fetch(`http://localhost:8080/api/dividends?userId=${userId}`),
                    fetch(`http://localhost:8080/api/performance?userId=${userEmail}&startDate=${startDate}&endDate=${endDate}`)
                ]);

                // Process Dashboard data
                if (dashRes.status === 'fulfilled' && dashRes.value.ok) {
                    const data = await dashRes.value.json();
                    setDashboardData(data);
                } else {
                    console.warn('Dashboard API failed, will calculate from holdings');
                }

                // Process Holdings data
                if (holdingsRes.status === 'fulfilled' && holdingsRes.value.ok) {
                    const data = await holdingsRes.value.json();
                    const holdingsArray = Array.isArray(data) ? data : (data.holdings || []);
                    setHoldings(holdingsArray);
                    console.log('Fetched holdings:', holdingsArray);
                } else {
                    console.warn('Holdings API failed:', holdingsRes);
                }

                // Process Dividends data
                if (dividendsRes.status === 'fulfilled' && dividendsRes.value.ok) {
                    const data = await dividendsRes.value.json();
                    const dividendsArray = Array.isArray(data) ? data : (data.dividends || []);
                    setDividends(dividendsArray);
                } else {
                    console.warn('Dividends API failed:', dividendsRes);
                }

                // Process Performance data
                if (perfRes.status === 'fulfilled' && perfRes.value.ok) {
                    const data = await perfRes.value.json();
                    setPerformance(data);
                } else {
                    console.warn('Performance API failed:', perfRes);
                }

            } catch (err) {
                console.error('Error fetching dashboard data:', err);
                setError(err instanceof Error ? err.message : 'Failed to load dashboard data');
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [userId, userEmail]); // Re-fetch when user changes

    // ==================== HELPER FUNCTIONS ====================
    const formatCurrency = (value: number | undefined | null): string => {
        if (value === undefined || value === null) return '$0.00';
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        }).format(value);
    };

    const formatPercentage = (value: number, total: number): string => {
        if (total === 0) return '0.00%';
        const percentage = (value / total) * 100;
        return `${percentage >= 0 ? '+' : ''}${percentage.toFixed(2)}%`;
    };

    // Calculate derived values
    const totalEquity = dashboardData?.totalEquity || holdings.reduce((sum, h) => sum + h.marketValue, 0);
    const unrealizedPL = dashboardData?.unrealizedPL || holdings.reduce((sum, h) => {
        return sum + (h.marketValue - (h.averageCost * h.quantity));
    }, 0);
    const totalDividends = dividends.reduce((sum, div) => sum + div.amount, 0);

    // ==================== LOADING STATE ====================
    if (loading) {
        return (
            <div className="flex items-center justify-center min-h-screen bg-gray-50">
                <div className="text-center">
                    <div className="inline-block animate-spin rounded-full h-16 w-16 border-b-4 border-blue-600 mb-4"></div>
                    <h3 className="text-xl font-semibold text-gray-700 mb-2">Loading Portfolio</h3>
                    <p className="text-gray-500">Fetching your holdings, performance, and dividends...</p>
                </div>
            </div>
        );
    }

    // ==================== ERROR STATE ====================
    if (error) {
        return (
            <div className="flex items-center justify-center min-h-screen bg-gray-50">
                <div className="bg-white border-l-4 border-red-500 rounded-lg shadow-lg p-6 max-w-md">
                    <div className="flex items-start">
                        <div className="flex-shrink-0">
                            <svg className="h-6 w-6 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                        </div>
                        <div className="ml-3 flex-1">
                            <h3 className="text-lg font-semibold text-red-800 mb-2">Error Loading Dashboard</h3>
                            <p className="text-sm text-red-700 mb-4">{error}</p>
                            <button
                                onClick={() => window.location.reload()}
                                className="bg-red-600 text-white px-4 py-2 rounded-lg hover:bg-red-700 transition-colors duration-200 text-sm font-medium"
                            >
                                Retry
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    // ==================== MAIN DASHBOARD RENDER ====================
    return (
        <div className="min-h-screen bg-gray-50 p-6 space-y-6">
            {/* ==================== SUMMARY CARDS ==================== */}
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-4">
                {/* Total Equity Card */}
                <div className="bg-gradient-to-br from-blue-500 to-blue-700 rounded-xl shadow-lg p-6 text-white transform hover:scale-105 transition-transform duration-200">
                    <div className="flex items-center justify-between mb-2">
                        <h3 className="text-sm font-medium opacity-90">Total Equity</h3>
                        <svg className="w-8 h-8 opacity-80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                    </div>
                    <p className="text-3xl font-bold mb-1">{formatCurrency(totalEquity)}</p>
                    <p className="text-xs opacity-75">{holdings.length} positions</p>
                </div>

                {/* Unrealized P&L Card */}
                <div className={`rounded-xl shadow-lg p-6 text-white transform hover:scale-105 transition-transform duration-200 ${unrealizedPL >= 0
                    ? 'bg-gradient-to-br from-green-500 to-green-700'
                    : 'bg-gradient-to-br from-red-500 to-red-700'
                    }`}>
                    <div className="flex items-center justify-between mb-2">
                        <h3 className="text-sm font-medium opacity-90">Unrealized P&L</h3>
                        <svg className="w-8 h-8 opacity-80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={unrealizedPL >= 0 ? "M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" : "M13 17h8m0 0V9m0 8l-8-8-4 4-6-6"} />
                        </svg>
                    </div>
                    <p className="text-3xl font-bold mb-1">{formatCurrency(unrealizedPL)}</p>
                    <p className="text-xs opacity-75">
                        {unrealizedPL >= 0 ? '↑' : '↓'} {formatPercentage(unrealizedPL, totalEquity - unrealizedPL)}
                    </p>
                </div>

                {/* Total Return Card */}
                <div className="bg-gradient-to-br from-purple-500 to-purple-700 rounded-xl shadow-lg p-6 text-white transform hover:scale-105 transition-transform duration-200">
                    <div className="flex items-center justify-between mb-2">
                        <h3 className="text-sm font-medium opacity-90">Total Return</h3>
                        <svg className="w-8 h-8 opacity-80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                        </svg>
                    </div>
                    <p className="text-3xl font-bold mb-1">
                        {performance?.totalAbsoluteReturn !== undefined
                            ? formatCurrency(performance.totalAbsoluteReturn)
                            : 'N/A'}
                    </p>
                    <p className="text-xs opacity-75">
                        {performance?.totalPercentReturn !== undefined
                            ? `${performance.totalPercentReturn >= 0 ? '+' : ''}${performance.totalPercentReturn.toFixed(2)}%`
                            : 'Last 30 days'}
                    </p>
                </div>

                {/* Dividends Card */}
                <div className="bg-gradient-to-br from-amber-500 to-amber-700 rounded-xl shadow-lg p-6 text-white transform hover:scale-105 transition-transform duration-200">
                    <div className="flex items-center justify-between mb-2">
                        <h3 className="text-sm font-medium opacity-90">Total Dividends</h3>
                        <svg className="w-8 h-8 opacity-80" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                    </div>
                    <p className="text-3xl font-bold mb-1">{formatCurrency(totalDividends)}</p>
                    <p className="text-xs opacity-75">{dividends.length} payments tracked</p>
                </div>
            </div>

            {/* ==================== HOLDINGS TABLE ==================== */}
            <div className="bg-white rounded-xl shadow-lg overflow-hidden">
                <div className="px-6 py-4 bg-gray-50 border-b border-gray-200">
                    <div className="flex items-center justify-between">
                        <h2 className="text-xl font-semibold text-gray-800">Current Holdings</h2>
                        <span className="text-sm text-gray-500">{holdings.length} positions</span>
                    </div>
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
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">% Change</th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {holdings.map((holding) => {
                                    const costBasis = holding.averageCost * holding.quantity;
                                    const gainLoss = holding.marketValue - costBasis;
                                    const gainLossPct = costBasis > 0
                                        ? ((holding.currentPrice - holding.averageCost) / holding.averageCost) * 100
                                        : 0;

                                    return (
                                        <tr key={holding.id} className="hover:bg-gray-50 transition-colors duration-150">
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                <div className="text-sm font-bold text-gray-900">{holding.symbol}</div>
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-700">
                                                {holding.quantity.toFixed(4)}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-700">
                                                {formatCurrency(holding.averageCost)}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-900 font-medium">
                                                {formatCurrency(holding.currentPrice)}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-right font-semibold text-gray-900">
                                                {formatCurrency(holding.marketValue)}
                                            </td>
                                            <td className={`px-6 py-4 whitespace-nowrap text-sm text-right font-semibold ${gainLoss >= 0 ? 'text-green-600' : 'text-red-600'
                                                }`}>
                                                {formatCurrency(gainLoss)}
                                            </td>
                                            <td className={`px-6 py-4 whitespace-nowrap text-sm text-right font-semibold ${gainLossPct >= 0 ? 'text-green-600' : 'text-red-600'
                                                }`}>
                                                {gainLossPct >= 0 ? '+' : ''}{gainLossPct.toFixed(2)}%
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    ) : (
                        <div className="px-6 py-16 text-center">
                            <svg className="mx-auto h-12 w-12 text-gray-400 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                            </svg>
                            <h3 className="text-lg font-medium text-gray-900 mb-2">No Holdings Found</h3>
                            {connectedBrokers.length > 0 ? (
                                <p className="text-gray-500 mb-4">
                                    Your account has no active positions. Click "Sync" to refresh, or add positions through your broker.
                                </p>
                            ) : (
                                <>
                                    <p className="text-gray-500 mb-4">Connect your brokerage account to see your portfolio</p>
                                    <a
                                        href="/settings"
                                        className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-blue-600 hover:bg-blue-700"
                                    >
                                        Connect Broker
                                    </a>
                                </>
                            )}
                        </div>
                    )}
                </div>
            </div>

            {/* ==================== DIVIDENDS TABLE ==================== */}
            <div className="bg-white rounded-xl shadow-lg overflow-hidden">
                <div className="px-6 py-4 bg-gray-50 border-b border-gray-200">
                    <div className="flex items-center justify-between">
                        <h2 className="text-xl font-semibold text-gray-800">Recent Dividends</h2>
                        <span className="text-sm text-gray-500">{dividends.length} payments</span>
                    </div>
                </div>
                <div className="overflow-x-auto">
                    {dividends.length > 0 ? (
                        <table className="w-full">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Symbol</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Amount</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Pay Date</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Ex-Date</th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {dividends.slice(0, 10).map((dividend) => (
                                    <tr key={dividend.id} className="hover:bg-gray-50 transition-colors duration-150">
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="text-sm font-bold text-gray-900">{dividend.symbol}</div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-green-600 font-semibold">
                                            {formatCurrency(dividend.amount)}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-700">
                                            {new Date(dividend.payDate).toLocaleDateString('en-US', {
                                                year: 'numeric',
                                                month: 'short',
                                                day: 'numeric'
                                            })}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-right text-gray-700">
                                            {dividend.exDate
                                                ? new Date(dividend.exDate).toLocaleDateString('en-US', {
                                                    year: 'numeric',
                                                    month: 'short',
                                                    day: 'numeric'
                                                })
                                                : 'N/A'
                                            }
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    ) : (
                        <div className="px-6 py-16 text-center">
                            <svg className="mx-auto h-12 w-12 text-gray-400 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                            <h3 className="text-lg font-medium text-gray-900 mb-2">No Dividends Tracked</h3>
                            <p className="text-gray-500">Dividend history will appear here once available</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default Dashboard;
