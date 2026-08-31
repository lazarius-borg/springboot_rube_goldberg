async function loadAnalytics() {
    try {
        const res = await fetch('/api/v1/analytics/summary');
        const data = await res.json();
        document.getElementById('mTotal').innerText = data.totalReservations;
        document.getElementById('mCancelled').innerText = data.cancelledReservations;
        document.getElementById('mWait').innerText = data.waitingListEntries;
        document.getElementById('mConv').innerText = (data.waitingListConversionRate || 0).toFixed(1) + '%';
    } catch (ignored) {}
}

loadAnalytics();
setInterval(loadAnalytics, 5000);
