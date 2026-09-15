#!/usr/bin/env python3
"""
Generates infrastructure/opensearch-dashboards/dashboards.ndjson
for OpenSearch Dashboards 2.19.0.
"""

import json
import os

OUTPUT_FILE = os.path.join(os.path.dirname(__file__), "dashboards.ndjson")

saved_objects = []

# ==============================================================================
# 1. Index Pattern: otel-logs (T005)
# ==============================================================================
saved_objects.append({
    "id": "otel-logs",
    "type": "index-pattern",
    "attributes": {
        "title": "otel-logs*",
        "timeFieldName": "timestamp"
    }
})

# ==============================================================================
# 2. Visualizations for User Story 1
# ==============================================================================

# T006: vis-logs-volume (Vertical Bar Date Histogram)
vis_state_logs_volume = {
    "title": "Log Event Volume",
    "type": "histogram",
    "params": {
        "type": "histogram",
        "grid": {"categoryLines": False},
        "categoryAxes": [{
            "id": "CategoryAxis-1",
            "type": "category",
            "position": "bottom",
            "show": True,
            "style": {},
            "scale": {"type": "linear"},
            "labels": {"show": True, "truncate": 100},
            "title": {}
        }],
        "valueAxes": [{
            "id": "ValueAxis-1",
            "name": "LeftAxis-1",
            "type": "value",
            "position": "left",
            "show": True,
            "style": {},
            "scale": {"type": "linear", "mode": "normal"},
            "labels": {"show": True, "rotate": 0, "filter": False, "truncate": 100},
            "title": {"text": "Count"}
        }],
        "seriesParams": [{
            "show": True,
            "type": "histogram",
            "mode": "normal",
            "data": {"label": "Count", "id": "1"},
            "valueAxis": "ValueAxis-1",
            "drawLinesBetweenPoints": True,
            "showCircles": True
        }],
        "addTooltip": True,
        "addLegend": False,
        "legendPosition": "right",
        "times": [],
        "addTimeMarker": False
    },
    "aggs": [
        {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
        {
            "id": "2",
            "enabled": True,
            "type": "date_histogram",
            "schema": "segment",
            "params": {
                "field": "timestamp",
                "timeRange": {"from": "now-15m", "to": "now"},
                "useNormalizedEsInterval": True,
                "interval": "auto",
                "drop_partials": False,
                "min_doc_count": 1,
                "extended_bounds": {}
            }
        }
    ]
}

saved_objects.append({
    "id": "vis-logs-volume",
    "type": "visualization",
    "attributes": {
        "title": "Log Event Volume",
        "visState": json.dumps(vis_state_logs_volume),
        "uiStateJSON": "{}",
        "description": "Log event frequency over time",
        "version": 1,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"query": "", "language": "kuery"},
                "filter": [],
                "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index"
            })
        }
    },
    "references": [
        {
            "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
            "type": "index-pattern",
            "id": "otel-logs"
        }
    ]
})

# T007: vis-logs-severity (Donut Chart terms on severity.keyword)
vis_state_logs_severity = {
    "title": "Severity Breakdown",
    "type": "pie",
    "params": {
        "type": "pie",
        "addTooltip": True,
        "addLegend": True,
        "legendPosition": "right",
        "isDonut": True,
        "labels": {"show": True, "values": True, "truncate": 100}
    },
    "aggs": [
        {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
        {
            "id": "2",
            "enabled": True,
            "type": "terms",
            "schema": "segment",
            "params": {
                "field": "severity.keyword",
                "size": 5,
                "order": "desc",
                "orderBy": "1",
                "otherBucket": True,
                "otherBucketLabel": "Other",
                "missingBucket": False
            }
        }
    ]
}

saved_objects.append({
    "id": "vis-logs-severity",
    "type": "visualization",
    "attributes": {
        "title": "Severity Breakdown",
        "visState": json.dumps(vis_state_logs_severity),
        "uiStateJSON": "{}",
        "description": "Log distribution across severity levels (INFO, WARN, ERROR)",
        "version": 1,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"query": "", "language": "kuery"},
                "filter": [],
                "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index"
            })
        }
    },
    "references": [
        {
            "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
            "type": "index-pattern",
            "id": "otel-logs"
        }
    ]
})

# T008: vis-logs-service (Horizontal Bar Chart terms on serviceName.keyword)
vis_state_logs_service = {
    "title": "Logs by Service",
    "type": "horizontal_bar",
    "params": {
        "type": "histogram",
        "grid": {"categoryLines": False},
        "categoryAxes": [{
            "id": "CategoryAxis-1",
            "type": "category",
            "position": "left",
            "show": True,
            "style": {},
            "scale": {"type": "linear"},
            "labels": {"show": True, "truncate": 100},
            "title": {}
        }],
        "valueAxes": [{
            "id": "ValueAxis-1",
            "name": "BottomAxis-1",
            "type": "value",
            "position": "bottom",
            "show": True,
            "style": {},
            "scale": {"type": "linear", "mode": "normal"},
            "labels": {"show": True, "rotate": 0, "filter": False, "truncate": 100},
            "title": {"text": "Count"}
        }],
        "seriesParams": [{
            "show": True,
            "type": "histogram",
            "mode": "normal",
            "data": {"label": "Count", "id": "1"},
            "valueAxis": "ValueAxis-1",
            "drawLinesBetweenPoints": True,
            "showCircles": True
        }],
        "addTooltip": True,
        "addLegend": False,
        "legendPosition": "right",
        "times": [],
        "addTimeMarker": False
    },
    "aggs": [
        {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
        {
            "id": "2",
            "enabled": True,
            "type": "terms",
            "schema": "segment",
            "params": {
                "field": "serviceName.keyword",
                "size": 10,
                "order": "desc",
                "orderBy": "1",
                "otherBucket": False,
                "missingBucket": False
            }
        }
    ]
}

saved_objects.append({
    "id": "vis-logs-service",
    "type": "visualization",
    "attributes": {
        "title": "Logs by Service",
        "visState": json.dumps(vis_state_logs_service),
        "uiStateJSON": "{}",
        "description": "Log record count partitioned by originating microservice",
        "version": 1,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"query": "", "language": "kuery"},
                "filter": [],
                "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index"
            })
        }
    },
    "references": [
        {
            "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
            "type": "index-pattern",
            "id": "otel-logs"
        }
    ]
})

# T009: search-logs-stream (Saved Search for Application Logs)
saved_objects.append({
    "id": "search-logs-stream",
    "type": "search",
    "attributes": {
        "title": "Application Log Stream",
        "description": "Interactive, filterable microservice log stream table",
        "hits": 0,
        "columns": ["timestamp", "serviceName", "severity", "traceId", "message"],
        "sort": [["timestamp", "desc"]],
        "version": 1,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"query": "", "language": "kuery"},
                "filter": [],
                "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index"
            })
        }
    },
    "references": [
        {
            "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
            "type": "index-pattern",
            "id": "otel-logs"
        }
    ]
})

# T010: application-logs-dashboard (Dashboard for User Story 1)
panels_app_logs = [
    {
        "version": "2.19.0",
        "type": "visualization",
        "gridData": {"x": 0, "y": 0, "w": 24, "h": 10, "i": "1"},
        "panelIndex": "1",
        "panelRefName": "panel_1"
    },
    {
        "version": "2.19.0",
        "type": "visualization",
        "gridData": {"x": 24, "y": 0, "w": 12, "h": 10, "i": "2"},
        "panelIndex": "2",
        "panelRefName": "panel_2"
    },
    {
        "version": "2.19.0",
        "type": "visualization",
        "gridData": {"x": 36, "y": 0, "w": 12, "h": 10, "i": "3"},
        "panelIndex": "3",
        "panelRefName": "panel_3"
    },
    {
        "version": "2.19.0",
        "type": "search",
        "gridData": {"x": 0, "y": 10, "w": 48, "h": 18, "i": "4"},
        "panelIndex": "4",
        "panelRefName": "panel_4"
    }
]

saved_objects.append({
    "id": "application-logs-dashboard",
    "type": "dashboard",
    "attributes": {
        "title": "Application Logs Dashboard",
        "description": "Deep microservice log exploration, severity breakdowns, and interactive log table",
        "hits": 0,
        "panelsJSON": json.dumps(panels_app_logs),
        "optionsJSON": json.dumps({"useMargins": True, "hidePanelTitles": False}),
        "version": 1,
        "timeRestore": True,
        "timeFrom": "now-15m",
        "timeTo": "now",
        "refreshInterval": {"pause": False, "value": 10000},
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"query": "", "language": "kuery"},
                "filter": []
            })
        }
    },
    "references": [
        {"name": "panel_1", "type": "visualization", "id": "vis-logs-volume"},
        {"name": "panel_2", "type": "visualization", "id": "vis-logs-severity"},
        {"name": "panel_3", "type": "visualization", "id": "vis-logs-service"},
        {"name": "panel_4", "type": "search", "id": "search-logs-stream"}
    ]
})

# ==============================================================================
# 3. User Story 2: Distributed Traces & Overview (T011 - T017)
# ==============================================================================

# T011: ss4o_traces (Index Pattern)
saved_objects.append({
    "id": "ss4o_traces",
    "type": "index-pattern",
    "attributes": {
        "title": "ss4o_traces-*",
        "timeFieldName": "startTime"
    }
})

# T012: vis-traces-throughput (Line / Area Chart date histogram on startTime)
vis_state_traces_throughput = {
    "title": "Trace Span Throughput",
    "type": "area",
    "params": {
        "type": "area",
        "grid": {"categoryLines": False},
        "categoryAxes": [{
            "id": "CategoryAxis-1",
            "type": "category",
            "position": "bottom",
            "show": True,
            "style": {},
            "scale": {"type": "linear"},
            "labels": {"show": True, "truncate": 100},
            "title": {}
        }],
        "valueAxes": [{
            "id": "ValueAxis-1",
            "name": "LeftAxis-1",
            "type": "value",
            "position": "left",
            "show": True,
            "style": {},
            "scale": {"type": "linear", "mode": "normal"},
            "labels": {"show": True, "rotate": 0, "filter": False, "truncate": 100},
            "title": {"text": "Spans"}
        }],
        "seriesParams": [{
            "show": True,
            "type": "area",
            "mode": "normal",
            "data": {"label": "Spans", "id": "1"},
            "valueAxis": "ValueAxis-1",
            "drawLinesBetweenPoints": True,
            "showCircles": True
        }],
        "addTooltip": True,
        "addLegend": False,
        "legendPosition": "right",
        "times": [],
        "addTimeMarker": False
    },
    "aggs": [
        {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
        {
            "id": "2",
            "enabled": True,
            "type": "date_histogram",
            "schema": "segment",
            "params": {
                "field": "startTime",
                "timeRange": {"from": "now-15m", "to": "now"},
                "useNormalizedEsInterval": True,
                "interval": "auto",
                "drop_partials": False,
                "min_doc_count": 1,
                "extended_bounds": {}
            }
        }
    ]
}

saved_objects.append({
    "id": "vis-traces-throughput",
    "type": "visualization",
    "attributes": {
        "title": "Trace Span Throughput",
        "visState": json.dumps(vis_state_traces_throughput),
        "uiStateJSON": "{}",
        "description": "Span volume and throughput over time",
        "version": 1,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"query": "", "language": "kuery"},
                "filter": [],
                "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index"
            })
        }
    },
    "references": [
        {
            "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
            "type": "index-pattern",
            "id": "ss4o_traces"
        }
    ]
})

# T013: vis-traces-status (Donut Chart on statusCode.keyword)
vis_state_traces_status = {
    "title": "Trace Status (OK vs ERROR)",
    "type": "pie",
    "params": {
        "type": "pie",
        "addTooltip": True,
        "addLegend": True,
        "legendPosition": "right",
        "isDonut": True,
        "labels": {"show": True, "values": True, "truncate": 100}
    },
    "aggs": [
        {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
        {
            "id": "2",
            "enabled": True,
            "type": "terms",
            "schema": "segment",
            "params": {
                "field": "statusCode.keyword",
                "size": 5,
                "order": "desc",
                "orderBy": "1",
                "otherBucket": False,
                "missingBucket": True
            }
        }
    ]
}

saved_objects.append({
    "id": "vis-traces-status",
    "type": "visualization",
    "attributes": {
        "title": "Trace Status (OK vs ERROR)",
        "visState": json.dumps(vis_state_traces_status),
        "uiStateJSON": "{}",
        "description": "Distribution of trace span status codes",
        "version": 1,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"query": "", "language": "kuery"},
                "filter": [],
                "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index"
            })
        }
    },
    "references": [
        {
            "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
            "type": "index-pattern",
            "id": "ss4o_traces"
        }
    ]
})

# T014: vis-traces-latency (Histogram of durationInNanos)
vis_state_traces_latency = {
    "title": "Span Duration (ms)",
    "type": "histogram",
    "params": {
        "type": "histogram",
        "grid": {"categoryLines": False},
        "categoryAxes": [{
            "id": "CategoryAxis-1",
            "type": "category",
            "position": "bottom",
            "show": True,
            "style": {},
            "scale": {"type": "linear"},
            "labels": {"show": True, "truncate": 100},
            "title": {"text": "Duration (Nanoseconds)"}
        }],
        "valueAxes": [{
            "id": "ValueAxis-1",
            "name": "LeftAxis-1",
            "type": "value",
            "position": "left",
            "show": True,
            "style": {},
            "scale": {"type": "linear", "mode": "normal"},
            "labels": {"show": True, "rotate": 0, "filter": False, "truncate": 100},
            "title": {"text": "Count"}
        }],
        "seriesParams": [{
            "show": True,
            "type": "histogram",
            "mode": "normal",
            "data": {"label": "Count", "id": "1"},
            "valueAxis": "ValueAxis-1",
            "drawLinesBetweenPoints": True,
            "showCircles": True
        }],
        "addTooltip": True,
        "addLegend": False,
        "legendPosition": "right",
        "times": [],
        "addTimeMarker": False
    },
    "aggs": [
        {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
        {
            "id": "2",
            "enabled": True,
            "type": "histogram",
            "schema": "segment",
            "params": {
                "field": "durationInNanos",
                "interval": 50000000,
                "extended_bounds": {}
            }
        }
    ]
}

saved_objects.append({
    "id": "vis-traces-latency",
    "type": "visualization",
    "attributes": {
        "title": "Span Duration (ms)",
        "visState": json.dumps(vis_state_traces_latency),
        "uiStateJSON": "{}",
        "description": "Span duration distribution across service operations",
        "version": 1,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"query": "", "language": "kuery"},
                "filter": [],
                "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index"
            })
        }
    },
    "references": [
        {
            "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
            "type": "index-pattern",
            "id": "ss4o_traces"
        }
    ]
})

# T015: search-traces-stream (Saved Search for Trace Spans)
saved_objects.append({
    "id": "search-traces-stream",
    "type": "search",
    "attributes": {
        "title": "Distributed Trace Spans",
        "description": "Table of recent trace spans with latency and status",
        "hits": 0,
        "columns": ["startTime", "serviceName", "name", "durationInNanos", "statusCode", "traceId"],
        "sort": [["startTime", "desc"]],
        "version": 1,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"query": "", "language": "kuery"},
                "filter": [],
                "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index"
            })
        }
    },
    "references": [
        {
            "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
            "type": "index-pattern",
            "id": "ss4o_traces"
        }
    ]
})

# T016: distributed-traces-dashboard (Dashboard for User Story 2)
panels_trace_dashboard = [
    {
        "version": "2.19.0",
        "type": "visualization",
        "gridData": {"x": 0, "y": 0, "w": 24, "h": 10, "i": "1"},
        "panelIndex": "1",
        "panelRefName": "panel_1"
    },
    {
        "version": "2.19.0",
        "type": "visualization",
        "gridData": {"x": 24, "y": 0, "w": 12, "h": 10, "i": "2"},
        "panelIndex": "2",
        "panelRefName": "panel_2"
    },
    {
        "version": "2.19.0",
        "type": "visualization",
        "gridData": {"x": 36, "y": 0, "w": 12, "h": 10, "i": "3"},
        "panelIndex": "3",
        "panelRefName": "panel_3"
    },
    {
        "version": "2.19.0",
        "type": "search",
        "gridData": {"x": 0, "y": 10, "w": 48, "h": 18, "i": "4"},
        "panelIndex": "4",
        "panelRefName": "panel_4"
    }
]

saved_objects.append({
    "id": "distributed-traces-dashboard",
    "type": "dashboard",
    "attributes": {
        "title": "Distributed Traces Dashboard",
        "description": "End-to-end request latency percentiles, error breakdowns, and span inspections",
        "hits": 0,
        "panelsJSON": json.dumps(panels_trace_dashboard),
        "optionsJSON": json.dumps({"useMargins": True, "hidePanelTitles": False}),
        "version": 1,
        "timeRestore": True,
        "timeFrom": "now-15m",
        "timeTo": "now",
        "refreshInterval": {"pause": False, "value": 10000},
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"query": "", "language": "kuery"},
                "filter": []
            })
        }
    },
    "references": [
        {"name": "panel_1", "type": "visualization", "id": "vis-traces-throughput"},
        {"name": "panel_2", "type": "visualization", "id": "vis-traces-status"},
        {"name": "panel_3", "type": "visualization", "id": "vis-traces-latency"},
        {"name": "panel_4", "type": "search", "id": "search-traces-stream"}
    ]
})

# T017: platform-observability-overview (Unified Dashboard)
panels_overview = [
    {
        "version": "2.19.0",
        "type": "visualization",
        "gridData": {"x": 0, "y": 0, "w": 24, "h": 10, "i": "1"},
        "panelIndex": "1",
        "panelRefName": "panel_1"
    },
    {
        "version": "2.19.0",
        "type": "visualization",
        "gridData": {"x": 24, "y": 0, "w": 24, "h": 10, "i": "2"},
        "panelIndex": "2",
        "panelRefName": "panel_2"
    },
    {
        "version": "2.19.0",
        "type": "visualization",
        "gridData": {"x": 0, "y": 10, "w": 16, "h": 10, "i": "3"},
        "panelIndex": "3",
        "panelRefName": "panel_3"
    },
    {
        "version": "2.19.0",
        "type": "visualization",
        "gridData": {"x": 16, "y": 10, "w": 16, "h": 10, "i": "4"},
        "panelIndex": "4",
        "panelRefName": "panel_4"
    },
    {
        "version": "2.19.0",
        "type": "visualization",
        "gridData": {"x": 32, "y": 10, "w": 16, "h": 10, "i": "5"},
        "panelIndex": "5",
        "panelRefName": "panel_5"
    }
]

saved_objects.append({
    "id": "platform-observability-overview",
    "type": "dashboard",
    "attributes": {
        "title": "Platform Observability Overview",
        "description": "Unified platform health, log event rates, trace throughput, and error distributions",
        "hits": 0,
        "panelsJSON": json.dumps(panels_overview),
        "optionsJSON": json.dumps({"useMargins": True, "hidePanelTitles": False}),
        "version": 1,
        "timeRestore": True,
        "timeFrom": "now-15m",
        "timeTo": "now",
        "refreshInterval": {"pause": False, "value": 10000},
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({
                "query": {"query": "", "language": "kuery"},
                "filter": []
            })
        }
    },
    "references": [
        {"name": "panel_1", "type": "visualization", "id": "vis-logs-volume"},
        {"name": "panel_2", "type": "visualization", "id": "vis-traces-throughput"},
        {"name": "panel_3", "type": "visualization", "id": "vis-logs-severity"},
        {"name": "panel_4", "type": "visualization", "id": "vis-traces-status"},
        {"name": "panel_5", "type": "visualization", "id": "vis-logs-service"}
    ]
})

# ==============================================================================
# 4. User Story 3: UI Config (T018)
# ==============================================================================
saved_objects.append({
    "id": "2.19.0",
    "type": "config",
    "attributes": {
        "defaultRoute": "/app/dashboards#/view/application-logs-dashboard",
        "timepicker:timeDefaults": "{\"from\":\"now-15m\",\"to\":\"now\"}",
        "timepicker:refreshIntervalDefaults": "{\"pause\":false,\"value\":10000}"
    }
})

# ==============================================================================
# Print NDJSON to stdout
# ==============================================================================
for obj in saved_objects:
    print(json.dumps(obj, separators=(",", ":")))
