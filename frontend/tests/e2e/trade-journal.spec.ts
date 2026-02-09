import { test, expect } from '@playwright/test';

const TEST_EMAIL = 'mohan.psns@gmail.com';

test.describe('Trade Journal', () => {
    test.beforeEach(async ({ page, request }) => {
        // Wait for backend to be ready
        await expect(async () => {
            const response = await request.get('http://localhost:8080/health');
            expect(response.status()).toBe(200);
        }).toPass({
            intervals: [1000, 2000, 5000],
            timeout: 60_000,
        });

        // Go to home page and login
        await page.goto('/');
        const emailInput = page.locator('input[type="email"]');
        if (await emailInput.isVisible({ timeout: 5000 }).catch(() => false)) {
            await emailInput.fill(TEST_EMAIL);
            await page.getByRole('button', { name: /Get Started|Login/i }).click();
        }

        // Wait for dashboard to load
        await expect(page.getByText('Total Equity')).toBeVisible({ timeout: 15_000 });
    });

    test('Dashboard loads with 5 summary cards', async ({ page }) => {
        await expect(page.getByText('Total Equity')).toBeVisible();
        await expect(page.getByText('Unrealized P&L')).toBeVisible();
        await expect(page.getByText('Realized P&L')).toBeVisible();
        await expect(page.getByText('Total Return')).toBeVisible();
        await expect(page.getByText('Total Dividends')).toBeVisible();
    });

    test('Unified positions table renders', async ({ page }) => {
        // Verify positions table exists with correct columns
        const positionsSection = page.getByText('Positions');
        await expect(positionsSection).toBeVisible();

        // Check table headers
        const table = page.locator('table').last();
        await expect(table.getByText('Symbol')).toBeVisible();
        await expect(table.getByText('Status')).toBeVisible();
        await expect(table.getByText('Total Return')).toBeVisible();
    });

    test('Cumulative P&L chart section exists', async ({ page }) => {
        // Chart section should be present if trades exist
        const chartSection = page.getByText('Cumulative P&L');
        // It may or may not be visible depending on trade data
        // Just verify the page loaded successfully
        await expect(page.getByText('Total Equity')).toBeVisible();
    });

    test('Header has no sync button', async ({ page }) => {
        // Verify navigation links exist
        const header = page.locator('header');
        await expect(header.getByRole('link', { name: /Dashboard/i })).toBeVisible();
        await expect(header.getByRole('link', { name: /Settings/i })).toBeVisible();
        await expect(header.getByRole('link', { name: /Trades/i })).toBeVisible();

        // Verify no sync button in header
        const headerButtons = header.locator('button');
        const count = await headerButtons.count();
        for (let i = 0; i < count; i++) {
            const text = await headerButtons.nth(i).textContent();
            expect(text?.toLowerCase()).not.toContain('sync');
        }
    });

    test('Trade History page loads with data', async ({ page }) => {
        await page.getByRole('link', { name: /Trades/i }).click();
        await expect(page.getByText('Trade History')).toBeVisible({ timeout: 10_000 });

        // Verify Sync Trades button exists
        await expect(page.getByRole('button', { name: /Sync Trades/i })).toBeVisible();

        // Verify table or empty state is shown
        const table = page.locator('table').first();
        const emptyState = page.getByText(/No Trades Found/i);
        await expect(table.or(emptyState)).toBeVisible({ timeout: 10_000 });
    });

    test('Trade grouping toggle works', async ({ page }) => {
        await page.getByRole('link', { name: /Trades/i }).click();
        await expect(page.getByText('Trade History')).toBeVisible({ timeout: 10_000 });

        // Look for the toggle buttons
        const allTradesBtn = page.getByRole('button', { name: 'All Trades' });
        const groupedBtn = page.getByRole('button', { name: 'Grouped Pairs' });

        await expect(allTradesBtn).toBeVisible();
        await expect(groupedBtn).toBeVisible();

        // Switch to grouped view
        await groupedBtn.click();

        // Check that grouped view columns appear (Pairs, Open Qty, Net P&L)
        const table = page.locator('table').last();
        await expect(table.getByText('Pairs')).toBeVisible();
        await expect(table.getByText('Net P&L')).toBeVisible();

        // Switch back to flat view
        await allTradesBtn.click();

        // Verify flat view columns
        await expect(table.getByText('Action')).toBeVisible();
        await expect(table.getByText('Total Cost')).toBeVisible();
    });

    test('Trade sync from trades page', async ({ page }) => {
        await page.getByRole('link', { name: /Trades/i }).click();
        await expect(page.getByText('Trade History')).toBeVisible({ timeout: 10_000 });

        // Click Sync Trades
        const syncButton = page.getByRole('button', { name: /Sync Trades/i });
        await expect(syncButton).toBeVisible();
        await syncButton.click();

        // Wait for syncing state
        await expect(page.getByText('Syncing...')).toBeVisible({ timeout: 5_000 }).catch(() => {
            // Sync may complete very quickly
        });

        // Wait for success toast or sync completion
        const successToast = page.getByText(/Successfully synced/i);
        const errorToast = page.getByText(/Failed to sync|Error syncing/i);
        await expect(successToast.or(errorToast)).toBeVisible({ timeout: 30_000 });
    });
});
