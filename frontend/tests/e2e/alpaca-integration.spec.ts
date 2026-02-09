import { test, expect } from '@playwright/test';

const TEST_EMAIL = 'mohan.psns@gmail.com';

test('Broker Integration - Dashboard and Trade History', async ({ page, request }) => {
    // 1. Wait for backend to be ready
    await expect(async () => {
        const response = await request.get('http://localhost:8080/health');
        expect(response.status()).toBe(200);
    }).toPass({
        intervals: [1000, 2000, 5000],
        timeout: 60_000,
    });

    // 2. Go to home page
    await page.goto('http://localhost:3000');

    // 3. Login
    const emailInput = page.locator('input[type="email"]');
    if (await emailInput.isVisible()) {
        await emailInput.fill(TEST_EMAIL);
        await page.getByRole('button', { name: /Get Started|Login/i }).click();
    }

    // 4. Wait for Dashboard to load
    await expect(page.getByText('Total Equity')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Unrealized P&L')).toBeVisible();
    await expect(page.getByText('Realized P&L')).toBeVisible();
    await expect(page.getByText('Total Return')).toBeVisible();
    await expect(page.getByText('Total Dividends')).toBeVisible();

    // 5. Navigate to Settings
    await page.getByRole('link', { name: /Settings/i }).click();

    // Check for connect or connected state
    const connectButton = page.getByRole('button', { name: /Connect Broker/i });
    if (await connectButton.isVisible({ timeout: 5000 }).catch(() => false)) {
        // Broker not connected yet - this is acceptable
    }

    // 6. Navigate to Trade History
    await page.goto('http://localhost:3000/trades');

    // Wait for trade history table or empty state
    const table = page.getByRole('table');
    const emptyState = page.getByText(/No Trades Found/i);
    await expect(table.or(emptyState)).toBeVisible({ timeout: 10_000 });

    if (await emptyState.isVisible()) {
        const syncButton = page.getByRole('button', { name: /Sync Trades/i });
        if (await syncButton.isVisible()) {
            await syncButton.click();
            await expect(table).toBeVisible({ timeout: 30_000 });
        }
    } else {
        await expect(table).toBeVisible();
    }

    // Check "Realized P&L" column header
    await expect(page.getByText('Realized P&L')).toBeVisible();

    // Check rows exist
    const rows = page.locator('tbody tr');
    await expect(rows).not.toHaveCount(0);

    // Check for Realized P&L column content
    const headers = page.locator('thead th');
    const headerTexts = await headers.allTextContents();
    const realizedPlIndex = headerTexts.findIndex(t => t.includes('Realized P&L'));

    if (realizedPlIndex !== -1) {
        const cell = rows.first().locator(`td:nth-child(${realizedPlIndex + 1})`);
        await expect(cell).not.toBeEmpty();
    }
});
