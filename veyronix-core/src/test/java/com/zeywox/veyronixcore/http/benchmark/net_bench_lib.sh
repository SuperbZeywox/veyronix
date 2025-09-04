#!/usr/bin/env bash
# net_bench_lib.sh — shared helpers for sequential & burst benchmarking

set -o pipefail

# ---------- Percentile (nearest-rank) from a *sorted* ms file ----------
percentile_nearest_rank_file() {
  # usage: percentile_nearest_rank_file <p> <sorted_file>
  local p="$1" sorted="$2"
  awk -v p="$p" '
    { a[++n]=$1 }
    END{
      if(n==0){print "NaN"; exit}
      x=(p/100.0)*n
      idx=int(x); if(x>idx) idx++
      if(idx<1) idx=1
      if(idx>n) idx=n
      printf "%.2f", a[idx]
    }' "$sorted"
}

# ---------- Compute pooled stats from an *unsorted* ms file ----------
compute_pooled() {
  # usage: compute_pooled <ms_file>
  local ms_file="$1"
  if [ ! -s "$ms_file" ]; then
    echo "0 NaN NaN NaN NaN NaN NaN NaN NaN"
    return
  fi
  local sorted="$ms_file.sorted"
  sort -n "$ms_file" > "$sorted"
  local N MIN MAX SUM AVG MEDIAN MODE P90 P95 P99
  N=$(wc -l < "$sorted" | tr -d ' ')
  MIN=$(head -n1 "$sorted")
  MAX=$(tail -n1 "$sorted")
  SUM=$(awk '{s+=$1} END{printf "%.2f", s+0}' "$sorted")
  AVG=$(awk -v s="$SUM" -v n="$N" 'BEGIN{printf "%.2f", (n>0?s/n:0)}')
  MEDIAN=$(
    awk '{a[NR]=$1} END{
      if(NR==0){print "NaN"; exit}
      if(NR%2==1){printf "%.2f", a[(NR+1)/2]}
      else {printf "%.2f", (a[NR/2]+a[NR/2+1])/2}
    }' "$sorted"
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
    }' "$sorted"
  )
  P90=$(percentile_nearest_rank_file 90 "$sorted")
  P95=$(percentile_nearest_rank_file 95 "$sorted")
  P99=$(percentile_nearest_rank_file 99 "$sorted")
  printf "%s %s %s %s %s %s %s %s %s\n" "$N" "$MIN" "$MEDIAN" "$AVG" "$MAX" "$MODE" "$P90" "$P95" "$P99"
}

# ---------- Compute per-run means and SD of per-run avg(ms) ----------
compute_means_and_sdavg() {
  # usage: compute_means_and_sdavg <perrun_file>
  # perrun_file rows: N MIN MEDIAN AVG MAX MODE P90 P95 P99
  local perrun_file="$1"
  if [ ! -s "$perrun_file" ]; then
    echo "0 NaN NaN NaN NaN NaN NaN NaN NaN  NaN"
    return
  fi
  awk '
    { n+=$1; c++;
      min+=$2; med+=$3; avg+=$4; max+=$5; mode+=$6; p90+=$7; p95+=$8; p99+=$9;
      s4+=$4; ss4+=$4*$4 }
    END{
      if(c==0){print "0 NaN NaN NaN NaN NaN NaN NaN NaN  NaN"; exit}
      m4=s4/c; sd4=sqrt((ss4/c)-(m4*m4))
      printf "%.0f %.2f %.2f %.2f %.2f %.2f %.2f %.2f %.2f  %.2f\n",
        n/c, min/c, med/c, avg/c, max/c, mode/c, p90/c, p95/c, p99/c, sd4
    }' "$perrun_file"
}

# ---------- Concurrency helpers for burst ----------
inc_conc() {
  (
    flock -x 200
    cur=$(cat "$CUR_CONC_FILE" 2>/dev/null || echo 0)
    cur=$((cur+1))
    echo "$cur" > "$CUR_CONC_FILE"
    peak=$(cat "$PEAK_CONC_FILE" 2>/dev/null || echo 0)
    if [ "$cur" -gt "$peak" ]; then echo "$cur" > "$PEAK_CONC_FILE"; fi
  ) 200>"$LOCKFILE"
}

dec_conc() {
  (
    flock -x 200
    cur=$(cat "$CUR_CONC_FILE" 2>/dev/null || echo 1)
    cur=$((cur-1))
    if [ "$cur" -lt 0 ]; then cur=0; fi
    echo "$cur" > "$CUR_CONC_FILE"
  ) 200>"$LOCKFILE"
}

do_request() {
  # env in: URL, HEADER, TIMES_SEC, CODES, LOCKFILE, CUR_CONC_FILE, PEAK_CONC_FILE
  inc_conc
  local out
  if [ -n "${HEADER:-}" ]; then
    out="$(curl -s -o /dev/null -H "$HEADER" -w "%{http_code} %{time_total}" "$URL" || true)"
  else
    out="$(curl -s -o /dev/null -w "%{http_code} %{time_total}" "$URL" || true)"
  fi
  local code tsec
  code="$(printf "%s" "$out" | awk '{print $1}')"
  tsec="$(printf "%s" "$out" | awk '{print $2}')"
  [ -z "${code:-}" ] && code="000"
  [ -z "${tsec:-}" ] && tsec="0.000"
  printf "%s\n" "$tsec" >> "$TIMES_SEC"
  printf "%s\n" "$code" >> "$CODES"
  dec_conc
}

export -f inc_conc dec_conc do_request

# ---------- Warm-up: 100 sequential requests, not measured ----------
warm_up_100() {
  # usage: warm_up_100 <url> <header>
  local url="$1" header="$2"
  for _ in $(seq 1 100); do
    if [ -n "$header" ]; then
      curl -s -o /dev/null -H "$header" "$url" || true
    else
      curl -s -o /dev/null "$url" || true
    fi
  done
}

# ---------- One *sequential* run of N requests ----------
sequential_run_once() {
  # usage: sequential_run_once <url> <header> <N> <pooled_ms_file> <perrun_file>
  local url="$1" header="$2" NREQ="$3" pooled_file="$4" perrun_file="$5"

  local TMP_DIR TIMES_SEC TIMES_MS TIMES_SORTED
  TMP_DIR="$(mktemp -d)"
  TIMES_SEC="$TMP_DIR/times_sec.txt"
  TIMES_MS="$TMP_DIR/times_ms.txt"
  TIMES_SORTED="$TMP_DIR/times_sorted.txt"
  : > "$TIMES_SEC"

  for _ in $(seq 1 "$NREQ"); do
    local out tsec
    if [ -n "$header" ]; then
      out="$(curl -s -o /dev/null -H "$header" -w "%{time_total}" "$url" || true)"
    else
      out="$(curl -s -o /dev/null -w "%{time_total}" "$url" || true)"
    fi
    tsec="${out:-0.000}"
    printf "%s\n" "$tsec" >> "$TIMES_SEC"
  done

  if [ ! -s "$TIMES_SEC" ]; then
    printf "0 NaN NaN NaN NaN NaN NaN NaN NaN\n" >> "$perrun_file"
    rm -rf "$TMP_DIR"
    return
  fi

  awk '{printf "%.2f\n", $1*1000}' "$TIMES_SEC" > "$TIMES_MS"
  sort -n "$TIMES_MS" > "$TIMES_SORTED"

  # stats for this sequential run
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

export -f warm_up_100 sequential_run_once compute_pooled compute_means_and_sdavg percentile_nearest_rank_file
