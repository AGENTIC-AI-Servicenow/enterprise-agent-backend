"use client";

import { MainLayout } from "@/components/layout/main-layout";
import { Card, CardContent, Badge, Button, Input } from "@/components/ui";
import { useIncidents } from "@/hooks/use-incidents";
import { Incident } from "@/types";
import { Search, Filter, RefreshCw } from "lucide-react";
import { useState } from "react";

export default function IncidentsPage() {
  const { incidents, isLoading, refetch } = useIncidents();
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
                            {incident.assigned_to || "Unassigned"}
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
