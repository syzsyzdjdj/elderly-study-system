// Use calendar arithmetic in UTC so DST and the browser timezone cannot shift date labels.
export function recentStudyTrend(rows, today, fromDate, toDate) {
  const end = new Date(`${today}T00:00:00Z`)
  if (!Number.isFinite(end.getTime())) return []
  const byDate = new Map(rows.map(row => [String(row.studyDate).slice(0, 10), row]))
  return Array.from({ length: 30 }, (_, i) => {
    const date = new Date(end)
    date.setUTCDate(date.getUTCDate() - 29 + i)
    const studyDate = date.toISOString().slice(0, 10)
    const row = byDate.get(studyDate)
    // Days after the saved run have not been analyzed; do not present them as zero activity.
    const covered = fromDate && toDate && studyDate >= fromDate && studyDate <= toDate
    return { studyDate,
      studyCount: row ? Number(row.studyCount ?? 0) : covered ? 0 : null,
      studyDurationMin: row ? Number(row.studyDurationMin ?? 0) : covered ? 0 : null }
  })
}
