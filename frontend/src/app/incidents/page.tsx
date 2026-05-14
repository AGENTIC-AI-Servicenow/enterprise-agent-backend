"use client";

import { MainLayout } from "@/components/layout/main-layout";
import { Card, CardContent, Badge, Button, Input } from "@/components/ui";
import { useIncidents } from "@/hooks/use-incidents";
import { Incident } from "@/types";
import { Search, Filter, RefreshCw } from "lucide-react";
import { useState, useEffect } from "react";
import { useIncident } from "@/hooks/use-incidents";

export default function IncidentsPage() {
  const { incidents, isLoading, refetch } = useIncidents();
  const [selectedIncidentId, setSelectedIncidentId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [filterState, setFilterState] = useState<string>("all");
  const [filterPriority, setFilterPriority] = useState<string>("all");
  const [sortBy, setSortBy] = useState<string>("date_desc");

  const filteredIncidents = incidents
    ?.filter((incident: Incident) => {
      const matchesSearch =
        incident.number.toLowerCase().includes(searchQuery.toLowerCase()) ||
        incident.short_description.toLowerCase().includes(searchQuery.toLowerCase());

      const matchesState =
        filterState === "all" || incident.state === filterState;

      const matchesPriority =
        filterPriority === "all" || incident.priority === filterPriority;

      return matchesSearch && matchesState && matchesPriority;
    })
    ?.sort((a, b) => {
      const dateA = new Date(a.opened_at).getTime();
      const dateB = new Date(b.opened_at).getTime();

      switch (sortBy) {
        case "date_asc":
          return dateA - dateB;
        case "date_desc":
          return dateB - dateA;
        case "priority":
          const priorityOrder = { Critical: 1, High: 2, Medium: 3, Low: 4 };
          return (
            (priorityOrder[a.priority as keyof typeof priorityOrder] || 5) -
            (priorityOrder[b.priority as keyof typeof priorityOrder] || 5)
          );
        case "number":
          return a.number.localeCompare(b.number);
        default:
          return 0;
      }
    });

  const getPriorityBadge = (priority: string) => {
    const badges = {
      Critical: <Badge variant="destructive">Critical</Badge>,
      High: <Badge variant="warning">High</Badge>,
      Medium: <Badge variant="info">Medium</Badge>,
      Low: <Badge variant="secondary">Low</Badge>,
    };
    return badges[priority as keyof typeof badges] || <Badge>Unknown</Badge>;
  };

  const getStateBadge = (state: string) => {
    const badges = {
      New: <Badge variant="info">New</Badge>,
      "In Progress": <Badge variant="warning">In Progress</Badge>,
      "On Hold": <Badge variant="secondary">On Hold</Badge>,
      Resolved: <Badge variant="success">Resolved</Badge>,
      Closed: <Badge variant="secondary">Closed</Badge>,
    };
    return badges[state as keyof typeof badges] || <Badge>{state}</Badge>;
  };

  return (
    <MainLayout
      title="Incidents"
      subtitle="Manage and track ServiceNow incidents"
    >
      <div className="space-y-4">
        {/* Filters and Search */}
        <Card>
          <CardContent className="pt-6">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              {/* Search */}
              <div className="relative flex-1">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  type="search"
                  placeholder="Search incidents by number or description..."
                  className="pl-9"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>

              {/* Filters */}
              <div className="flex items-center gap-2">
                <select
                  className="h-10 rounded-md border px-3 text-sm"
                  value={filterState}
                  onChange={(e) => setFilterState(e.target.value)}
                >
                  <option value="all">All States</option>
                  <option value="New">New</option>
                  <option value="In Progress">In Progress</option>
                  <option value="On Hold">On Hold</option>
                  <option value="Resolved">Resolved</option>
                  <option value="Closed">Closed</option>
                </select>

                <select
                  className="h-10 rounded-md border px-3 text-sm"
                  value={filterPriority}
                  onChange={(e) => setFilterPriority(e.target.value)}
                >
                  <option value="all">All Priorities</option>
                  <option value="Critical">Critical</option>
                  <option value="High">High</option>
                  <option value="Medium">Medium</option>
                  <option value="Low">Low</option>
                </select>

                <select
                  className="h-10 rounded-md border px-3 text-sm"
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value)}
                >
                  <option value="date_desc">Newest First</option>
                  <option value="date_asc">Oldest First</option>
                  <option value="priority">Priority</option>
                  <option value="number">Number</option>
                </select>

                <Button
                  variant="outline"
                  size="icon"
                  onClick={() => refetch()}
                  disabled={isLoading}
                >
                  <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Incidents List */}
        <Card>
          <CardContent className="p-0">
            {isLoading ? (
              <div className="flex items-center justify-center py-12">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
              </div>
            ) : filteredIncidents && filteredIncidents.length === 0 ? (
              <div className="py-12 text-center text-muted-foreground">
                No incidents found
              </div>
            ) : (
              <div className="divide-y">
                {filteredIncidents?.map((incident: Incident) => (
                  <div
                    key={incident.sys_id}
                    onClick={() => setSelectedIncidentId(incident.sys_id)}
                    className="p-6 transition-colors hover:bg-muted/50 cursor-pointer"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 space-y-2">
                        {/* Header */}
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-mono text-sm font-medium">
                            {incident.number}
                          </span>
                          {getPriorityBadge(incident.priority)}
                          {getStateBadge(incident.state)}
                        </div>

                        {/* Description */}
                        <h3 className="font-semibold text-lg">
                          {incident.short_description}
                        </h3>

                        {/* Metadata */}
                        <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
                          <div>
                            <span className="font-medium">Category:</span>{" "}
                            {incident.category || "Uncategorized"}
                          </div>
                          <div>
                            <span className="font-medium">Assigned to:</span>{" "}
                            {(() => {
                              const ia: any = incident;
                              return typeof ia.assigned_to === "object"
                                ? ia.assigned_to?.value
                                : ia.assigned_to || "Unassigned";
                            })()}
                          </div>
                          <div>
                            <span className="font-medium">Impact:</span>{" "}
                            {incident.impact}
                          </div>
                          <div>
                            <span className="font-medium">Urgency:</span>{" "}
                            {incident.urgency}
                          </div>
                        </div>
                      </div>

                      {/* Timestamp */}
                      <div className="text-right text-sm text-muted-foreground whitespace-nowrap">
                        <div className="font-medium">
                          {new Date(incident.opened_at).toLocaleDateString()}
                        </div>
                        <div className="text-xs">
                          {new Date(incident.opened_at).toLocaleTimeString()}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* INCIDENT DETAIL MODAL */}
        {selectedIncidentId && (
          <IncidentDetailModal
            incidentId={selectedIncidentId}
            onClose={() => setSelectedIncidentId(null)}
          />
        )}

        {/* Pagination */}
        {filteredIncidents && filteredIncidents.length > 0 && (
          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">
              Showing {filteredIncidents.length} of {incidents?.length || 0} incidents
            </div>
          </div>
        )}
      </div>
    </MainLayout>
  );
}

/* ===============================
   INCIDENT DETAIL MODAL COMPONENT
================================= */

function IncidentDetailModal({
  incidentId,
  onClose,
}: {
  incidentId: string;
  onClose: () => void;
}) {
  const { data: incident, isLoading } = useIncident(incidentId);
  const [isEditing, setIsEditing] = useState(false);

  // 🔒 Lock background scroll while modal is open
  useEffect(() => {
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = "auto";
    };
  }, []);

  const stateMap: any = {
    "1": "New",
    "2": "In Progress",
    "3": "On Hold",
    "6": "Resolved",
    "7": "Closed",
  };

  const priorityMap: any = {
    "1": "Critical",
    "2": "High",
    "3": "Medium",
    "4": "Low",
    "5": "Low",
  };

  const getStateLabel = (s: string) => stateMap[s] || s;
  const getPriorityLabel = (p: string) => priorityMap[p] || p;

  return (
    <div className="fixed top-0 left-0 w-screen h-screen z-[9999] bg-black/50 flex items-center justify-center !mt-0 !mb-0">
      <div className="w-full max-w-[1000px] max-h-[95vh] rounded-2xl bg-white shadow-2xl border flex flex-col overflow-hidden">
        <div className="flex justify-between items-start px-8 pt-8 pb-6 border-b bg-white">
          <div>
            <div className="text-2xl font-bold tracking-tight">
              {incident?.number}
            </div>
            <div className="flex gap-2 mt-3">
              {incident && (
                <>
                  <Badge>{getStateLabel((incident as any).state)}</Badge>
                  <Badge variant="secondary">
                    {getPriorityLabel((incident as any).priority)}
                  </Badge>
                </>
              )}
            </div>
          </div>

          <div className="flex gap-3">
            <Button
              variant={isEditing ? "destructive" : "default"}
              size="sm"
              onClick={() => setIsEditing(!isEditing)}
            >
              {isEditing ? "Cancel Editing" : "Edit Incident"}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={onClose}
            >
              Close
            </Button>
          </div>
        </div>

        {isLoading || !incident ? (
          <div className="flex-1 flex items-center justify-center text-muted-foreground">
            Loading incident details...
          </div>
        ) : (() => {
            const inc: any = incident;
            return (
            <div className="flex-1 overflow-y-auto px-8 py-8 space-y-10">

              {/* HEADER SUMMARY */}
              <div className="flex items-center justify-between border-b pb-4">
                <div>
                  <h3 className="text-lg font-semibold">{incident.short_description}</h3>
                  <div className="text-sm text-muted-foreground mt-1">
                    {incident.category || "Uncategorized"}
                  </div>
                </div>
                <div className="text-right text-sm text-muted-foreground">
                  <div>
                    {incident.opened_at
                      ? new Date(incident.opened_at).toLocaleString()
                      : "—"}
                  </div>
                </div>
              </div>

              {/* DETAILS GRID */}
              <div className="grid grid-cols-2 gap-10 text-sm">

                <div>
                  <div className="font-medium text-muted-foreground">State</div>
                  {isEditing ? (
                    <select className="border rounded px-2 py-1">
                      <option value="1">New</option>
                      <option value="2">In Progress</option>
                      <option value="3">On Hold</option>
                      <option value="6">Resolved</option>
                      <option value="7">Closed</option>
                    </select>
                  ) : (
                    <div>{getStateLabel(inc.state)}</div>
                  )}
                </div>

                <div>
                  <div className="font-medium text-muted-foreground">Priority</div>
                  {isEditing ? (
                    <select className="border rounded px-2 py-1">
                      <option value="1">Critical</option>
                      <option value="2">High</option>
                      <option value="3">Medium</option>
                      <option value="4">Low</option>
                      <option value="5">Low</option>
                    </select>
                  ) : (
                    <div>{getPriorityLabel(inc.priority)}</div>
                  )}
                </div>

                <div>
                  <div className="font-medium text-muted-foreground">Impact</div>
                  <div>{incident.impact}</div>
                </div>

                <div>
                  <div className="font-medium text-muted-foreground">Urgency</div>
                  <div>{incident.urgency}</div>
                </div>

                <div>
                  <div className="font-medium text-muted-foreground">Assigned To</div>
                  <div>
                    {typeof inc.assigned_to === "object"
                      ? inc.assigned_to?.value
                      : inc.assigned_to || "Unassigned"}
                  </div>
                </div>

                <div>
                  <div className="font-medium text-muted-foreground">Caller Sys ID</div>
                  <div className="break-all text-xs">
                    {typeof inc.caller_id === "object"
                      ? inc.caller_id?.value
                      : inc.caller_id}
                  </div>
                </div>

                <div>
                  <div className="font-medium text-muted-foreground">Created On</div>
                  <div>
                    {inc.sys_created_on
                      ? new Date(inc.sys_created_on).toLocaleString()
                      : "—"}
                  </div>
                </div>

                <div>
                  <div className="font-medium text-muted-foreground">Updated On</div>
                  <div>
                    {inc.sys_updated_on
                      ? new Date(inc.sys_updated_on).toLocaleString()
                      : "—"}
                  </div>
                </div>

              </div>

              {/* DESCRIPTION */}
              <div className="bg-slate-50 border rounded-xl p-6">
                <div className="font-semibold text-base mb-3">Description</div>
                <div className="whitespace-pre-wrap text-sm leading-relaxed">
                  {incident.description || "No description provided"}
                </div>
              </div>

              {/* TIMELINE */}
              <div className="bg-slate-50 border rounded-xl p-6">
                <div className="font-semibold text-base mb-6">Activity Timeline</div>
                <div className="relative border-l pl-6 space-y-6">
                  <div>
                    <div className="absolute -left-[9px] w-4 h-4 bg-blue-500 rounded-full"></div>
                    <div className="font-medium">Incident Created</div>
                    <div className="text-sm text-muted-foreground">
                      {inc.sys_created_on}
                    </div>
                  </div>
                  <div>
                    <div className="absolute -left-[9px] w-4 h-4 bg-amber-500 rounded-full"></div>
                    <div className="font-medium">Last Updated</div>
                    <div className="text-sm text-muted-foreground">
                      {inc.sys_updated_on}
                    </div>
                  </div>
                </div>
              </div>

              {/* RAW JSON (collapsible style visual improvement) */}
              <details className="border rounded-xl p-4 bg-slate-50">
                <summary className="cursor-pointer font-medium text-sm">
                  Technical JSON Payload
                </summary>
                <pre className="text-xs whitespace-pre-wrap break-all mt-4 max-h-[250px] overflow-auto">
                  {JSON.stringify(incident, null, 2)}
                </pre>
              </details>

            </div>
            );
        })()}
      </div>
    </div>
  );
}
