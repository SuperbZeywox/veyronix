#!/usr/bin/env bash
# main.sh — For each endpoint: warm up (100), then for each repeat do:
#  - 100 sequential requests (measured)
#  - 1000-request burst (measured)
#  - sleep (configurable, default 20s)
# Outputs:
#   Summary: Sequential — pooled (all sequential samples)
#   Burst (1000) — Run-averaged results (means of per-run metrics)
#   Per-endpoint variability notes for burst run-avg
# Deps: bash, curl, awk, sed, sort, uniq, flock (util-linux)

set -u -o pipefail

# --- Config ---

#PRODUCT_ID='09c938e9-ee4f-4af5-b97b-0af8259617d2'  # hardcoded
#./main.sh 09c938e9-ee4f-4af5-b97b-0af8259617d2

BASE_URL='http://localhost:8080'
CAT='Computer'
# Require exactly one positional argument: PRODUCT_ID
if [[ $# -lt 1 ]]; then
  echo "ERROR: PRODUCT_ID not provided. Usage: $(basename "$0") PRODUCT_ID" >&2
  exit 2
fi
if [[ $# -gt 1 ]]; then
  echo "ERROR: Only one argument allowed — PRODUCT_ID. Usage: $(basename "$0") PRODUCT_ID" >&2
  exit 2
fi

PRODUCT_ID="$1"

TOTAL_REQUESTS_BURST=1000          # per burst
TOTAL_REQUESTS_SEQ=100             # per sequential run
REPEATS=5                          # cycles per endpoint: [seq(100) -> burst(1000) -> sleep]
SLEEP_BETWEEN=20                   # seconds between cycles
PRINT_OBS_PEAK=false               # set true to print observed peak client concurrency per burst

#TOTAL_REQUESTS_BURST=100           # per burst
#TOTAL_REQUESTS_SEQ=100             # per sequential run
#REPEATS=1                          # cycles per endpoint: [seq(100) -> burst(1000) -> sleep]
#SLEEP_BETWEEN=20                   # seconds between cycles
#PRINT_OBS_PEAK=false               # set true to print observed peak client concurrency per burst


# Optional output override
#REPORT_FILE_DEFAULT="burst_report_$(date +%Y%m%d_%H%M%S).txt"
REPORT_FILE_DEFAULT="burst_report.txt"
REPORT_FILE="${REPORT_FILE:-$REPORT_FILE_DEFAULT}"

# Args
OPTIND=1
while getopts ":o:" opt; do
  case $opt in
    o) REPORT_FILE="$OPTARG" ;;
    \?) echo "Usage: $0 [-o OUTPUT_FILE]" >&2; exit 2 ;;
  esac
done

# --- Locate and source library ---
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=net_bench_lib.sh
. "$SCRIPT_DIR/net_bench_lib.sh"

# --- Endpoints + headers (parallel arrays) ---
URLS=(
  "$BASE_URL/products?category=$CAT"
  "$BASE_URL/products?category=$CAT&inStock=true"
  "$BASE_URL/products/$PRODUCT_ID"
)
HEADERS=(
  "Accept-Encoding: gzip"   # only first endpoint
  ""
  ""
)

# --- Files to aggregate rows we’ll print later ---
ROOT_TMP="$(mktemp -d)"
trap 'rm -rf "$ROOT_TMP" >/dev/null 2>&1 || true' EXIT

BURST_POOLED_TSV="$ROOT_TMP/burst_pooled.tsv"   # computed but not printed (kept for future use)
BURST_AVG_TSV="$ROOT_TMP/burst_avg.tsv"
SEQ_POOLED_TSV="$ROOT_TMP/seq_pooled.tsv"
SD_NOTES_TSV="$ROOT_TMP/sd_notes.tsv"
: > "$BURST_POOLED_TSV" "$BURST_AVG_TSV" "$SEQ_POOLED_TSV" "$SD_NOTES_TSV"

# --- Header (run info) ---
{
  echo "Run: $(date -u +"%Y-%m-%dT%H:%M:%SZ")  Host: zeywox  Base: $BASE_URL"
  echo "Plan per endpoint: warm-up(100) → [repeat x$REPEATS: sequential(100) → burst(1000) → sleep ${SLEEP_BETWEEN}s]"
  echo
} | tee "$REPORT_FILE"

TOTAL_CYCLES=$(( REPEATS * ${#URLS[@]} ))
CYCLE_COUNTER=1

# ---------- Burst runner (reuses helpers from lib) ----------
run_burst_once() {
  # usage: run_burst_once <url> <header> <pooled_ms_file> <perrun_file>
  local url="$1" header="$2" pooled_file="$3" perrun_file="$4"

  local TMP_DIR TIMES_SEC TIMES_MS TIMES_SORTED CODES LOCKFILE CUR_CONC_FILE PEAK_CONC_FILE
  TMP_DIR="$(mktemp -d "$ROOT_TMP/burst.XXXXXX")"
  TIMES_SEC="$TMP_DIR/times_sec.txt"
  TIMES_MS="$TMP_DIR/times_ms.txt"
  TIMES_SORTED="$TMP_DIR/times_sorted.txt"
  CODES="$TMP_DIR/codes.txt"
  LOCKFILE="$TMP_DIR/lockfile"
  CUR_CONC_FILE="$TMP_DIR/cur_conc"
  PEAK_CONC_FILE="$TMP_DIR/peak_conc"
  : > "$TIMES_SEC" ; : > "$CODES" ; : > "$LOCKFILE"
  echo 0 > "$CUR_CONC_FILE"
  echo 0 > "$PEAK_CONC_FILE"

  export URL="$url" HEADER="$header" TIMES_SEC CODES LOCKFILE CUR_CONC_FILE PEAK_CONC_FILE

  # fire the burst
  for _ in $(seq 1 "$TOTAL_REQUESTS_BURST"); do
    bash -c 'do_request' &
  done
  wait

  if $PRINT_OBS_PEAK; then
    OBS_PEAK=$(cat "$PEAK_CONC_FILE" 2>/dev/null || echo 0)
    echo "    (observed peak client concurrency ≈ $OBS_PEAK)" | tee -a "$REPORT_FILE"
  fi

  if [ ! -s "$TIMES_SEC" ]; then
    printf "0 NaN NaN NaN NaN NaN NaN NaN NaN\n" >> "$perrun_file"
    rm -rf "$TMP_DIR"
    return
  fi

  awk '{printf "%.2f\n", $1*1000}' "$TIMES_SEC" > "$TIMES_MS"
  sort -n "$TIMES_MS" > "$TIMES_SORTED"

  local N MIN MAX SUM AVG MEDIAN MODE P90 P95 P99
  N=$(wc -l < "$TIMES_SORTED" | tr -d ' ')
  MIN=$(head -n1 "$TIMES_SORTED")
  MAX=$(tail -n1 "$TIMES_SORTED")
  SUM=$(awk '{s+=$1} END{printf "%.2f", s+0}' "$TIMES_SORTED")
  AVG=$(awk -v s="$SUM" -v n="$N" 'BEGIN{printf "%.2f", (n>0?s/n:0)}')
  MEDIAN=$(
    awk '{a[NR]=$1} END{
      if(NR==0){print "NaN"; exit}
      if(NR%2==1){printf "%.2f", a[(NR+1)/2]}
      else {printf "%.2f", (a[NR/2]+a[NR/2+1])/2}
    }' "$TIMES_SORTED"
  )
  MODE=$(
    awk '{
      c[$1]++
    } END {
      mc=0; mv=""
      for(k in c){
        if(c[k]>mc || (c[k]==mc && k<mv)){ mc=c[k]; mv=k }
      }
      if(mv==""){ print "NaN" } else { printf "%.2f", mv }
    }' "$TIMES_SORTED"
  )
  P90=$(percentile_nearest_rank_file 90 "$TIMES_SORTED")
  P95=$(percentile_nearest_rank_file 95 "$TIMES_SORTED")
  P99=$(percentile_nearest_rank_file 99 "$TIMES_SORTED")

  # accumulate
  cat "$TIMES_MS" >> "$pooled_file"
  printf "%s %s %s %s %s %s %s %s %s\n" "$N" "$MIN" "$MEDIAN" "$AVG" "$MAX" "$MODE" "$P90" "$P95" "$P99" >> "$perrun_file"

  rm -rf "$TMP_DIR"
}

export -f run_burst_once

# ---------- Main loop per endpoint ----------
for idx in "${!URLS[@]}"; do
  URL="${URLS[$idx]}"
  HDR="${HEADERS[$idx]}"
  ENDPOINT="$(printf "%s" "$URL" | sed -E 's#https?://[^/]+##')"

  echo "Endpoint #$((idx+1)) warm-up (100): $ENDPOINT" | tee -a "$REPORT_FILE"
  warm_up_100 "$URL" "$HDR"

  # files to collect stats for this endpoint
  SEQ_POOLED_FILE="$ROOT_TMP/seq_${idx}.ms"
  SEQ_PERRUN_FILE="$ROOT_TMP/seq_${idx}_perrun.txt"
  BURST_POOLED_FILE="$ROOT_TMP/burst_${idx}.ms"
  BURST_PERRUN_FILE="$ROOT_TMP/burst_${idx}_perrun.txt"
  : > "$SEQ_POOLED_FILE" "$SEQ_PERRUN_FILE" "$BURST_POOLED_FILE" "$BURST_PERRUN_FILE"

  for r in $(seq 1 "$REPEATS"); do
    echo "  cycle $r/$REPEATS → sequential(100) then burst(1000)" | tee -a "$REPORT_FILE"
    # 1) sequential 100
    sequential_run_once "$URL" "$HDR" "$TOTAL_REQUESTS_SEQ" "$SEQ_POOLED_FILE" "$SEQ_PERRUN_FILE"
    # 2) burst 1000
    run_burst_once "$URL" "$HDR" "$BURST_POOLED_FILE" "$BURST_PERRUN_FILE"
    # 3) sleep
    if [ $CYCLE_COUNTER -lt $TOTAL_CYCLES ]; then sleep "$SLEEP_BETWEEN"; fi
    CYCLE_COUNTER=$((CYCLE_COUNTER+1))
  done

  # Aggregate results for this endpoint (pooled & run-avg)
  read Nseq MINseq MEDseq AVGseq MAXseq MODEseq P90seq P95seq P99seq < <(compute_pooled "$SEQ_POOLED_FILE")
  read Np   MINp   MEDp   AVGp   MAXp   MODEp   P90p   P95p   P99p   < <(compute_pooled "$BURST_POOLED_FILE")
  read Navg MINavg MEDavg AVGavg MAXavg MODEavg P90avg P95avg P99avg SDAVG < <(compute_means_and_sdavg "$BURST_PERRUN_FILE")

  # Save rows for later printing
#  printf "%d\t%s\t%d\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
#    "$((idx+1))" "$ENDPOINT" "$Np" "$MINp" "$MEDp" "$AVGp" "$MAXp" "$MODEp" "$P90p" "$P95p" "$P99p" >> "$BURST_POOLED_TSV"

  printf "%d\t%s\t%d\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "$((idx+1))" "$ENDPOINT" "$Navg" "$MINavg" "$MEDavg" "$AVGavg" "$MAXavg" "$MODEavg" "$P90avg" "$P95avg" "$P99avg" >> "$BURST_AVG_TSV"

  printf "%d\t%s\t%d\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "$((idx+1))" "$ENDPOINT" "$Nseq" "$MINseq" "$MEDseq" "$AVGseq" "$MAXseq" "$MODEseq" "$P90seq" "$P95seq" "$P99seq" >> "$SEQ_POOLED_TSV"

  printf "%s\t%s\n" "$SDAVG" "$ENDPOINT" >> "$SD_NOTES_TSV"
done

# ---------- Print tables (Sequential pooled + Burst run-averaged only) ----------
{
  echo
  echo "Summary: Sequential — pooled results (all sequential samples combined per endpoint; 100 per run)"
  echo "+---+----------------------------------------------------------+-------+----------+-------------+----------+----------+-----------+----------+----------+----------+"
  printf "| # | %-58s | %5s | %8s | %11s | %8s | %8s | %9s | %8s | %8s | %8s |\n" \
         "Endpoint" "total" "min (ms)" "median (ms)" "avg (ms)" "max (ms)" "mode (ms)" "p90 (ms)" "p95 (ms)" "p99 (ms)"
  echo "+---+----------------------------------------------------------+-------+----------+-------------+----------+----------+-----------+----------+----------+----------+"
  while IFS=$'\t' read -r idx ep total min med avg max mode p90 p95 p99; do
    [ -z "${idx:-}" ] && continue
    printf "| %d | %-58s | %5d | %8s | %11s | %8s | %8s | %9s | %8s | %8s | %8s |\n" \
           "$idx" "$ep" "$total" "$min" "$med" "$avg" "$max" "$mode" "$p90" "$p95" "$p99"
  done < "$SEQ_POOLED_TSV"
  echo "+---+----------------------------------------------------------+-------+----------+-------------+----------+----------+-----------+----------+----------+----------+"
} | tee -a "$REPORT_FILE"

{
  echo
  echo "Burst (1000) — Run-averaged results (means of per-run metrics; n=$REPEATS)"
  echo "+---+----------------------------------------------------------+-------+----------+-------------+----------+----------+-----------+----------+----------+----------+"
  printf "| # | %-58s | %5s | %8s | %11s | %8s | %8s | %9s | %8s | %8s | %8s |\n" \
         "Endpoint" "total/run" "min (ms)" "median (ms)" "avg (ms)" "max (ms)" "mode (ms)" "p90 (ms)" "p95 (ms)" "p99 (ms)"
  echo "+---+----------------------------------------------------------+-------+----------+-------------+----------+----------+-----------+----------+----------+----------+"
  while IFS=$'\t' read -r idx ep total min med avg max mode p90 p95 p99; do
    [ -z "${idx:-}" ] && continue
    printf "| %d | %-58s | %5d | %8s | %11s | %8s | %8s | %9s | %8s | %8s | %8s |\n" \
           "$idx" "$ep" "$total" "$min" "$med" "$avg" "$max" "$mode" "$p90" "$p95" "$p99"
  done < "$BURST_AVG_TSV"
  echo "+---+----------------------------------------------------------+-------+----------+-------------+----------+----------+-----------+----------+----------+----------+"
} | tee -a "$REPORT_FILE"

# Appendix: variability of the per-run mean avg(ms) for burst
if [ -s "$SD_NOTES_TSV" ]; then
  {
    echo
    echo "Per-endpoint variability (burst run-avg 'avg (ms)' ±SD over $REPEATS runs):"
    while IFS=$'\t' read -r sd ep; do
      [ -z "${sd:-}" ] && continue
      printf "  - %s: ±%.2f ms\n" "$ep" "$sd"
    done < "$SD_NOTES_TSV"
  } | tee -a "$REPORT_FILE"
fi

echo "Saved report to: $REPORT_FILE" >&2
