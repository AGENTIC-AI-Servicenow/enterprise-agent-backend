"use client";

import { MainLayout } from "@/components/layout/main-layout";
import { Card, CardContent, CardDescription, CardHeader, CardTitle, Badge } from "@/components/ui";
import { useIncidents } from "@/hooks/use-incidents";
import { Incident } from "@/types";
import { AlertCircle, CheckCircle, Clock, TrendingUp } from "lucide-react";

export default function DashboardPage() {
  const { incidents, isLoading } = useIncidents();

  // Calculate metrics
  const totalIncidents = incidents?.length || 0;
  const openIncidents = incidents?.filter((i: Incident) => i.state === "New" || i.state === "In Progress").length || 0;
  const resolvedIncidents = incidents?.filter((i: Incident) => i.state === "Resolved").length || 0;
  const highPriorityIncidents = incidents?.filter((i: Incident) => i.priority === "Critical" || i.priority === "High").length || 0;

  const metrics = [
    {
      title: "Total Incidents",
      value: totalIncidents,
      description: "All incidents in the system",
      icon: AlertCircle,
      trend: "+12% from last month",
      color: "text-blue-500",
    },
    {
      title: "Open Incidents",
      value: openIncidents,
      description: "Currently active incidents",
      icon: Clock,
      trend: "-5% from last week",
      color: "text-orange-500",
    },
    {
      title: "Resolved",
      value: resolvedIncidents,
      description: "Successfully resolved",
      icon: CheckCircle,
      trend: "+8% from last week",
      color: "text-green-500",
    },
    {
      title: "High Priority",
      value: highPriorityIncidents,
      description: "Needs immediate attention",
      icon: TrendingUp,
      trend: "2 critical",
      color: "text-red-500",
    },
  ];

  const recentIncidents = incidents?.slice(0, 5) || [];

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
      title="Dashboard"
      subtitle="Overview of your ServiceNow incidents and metrics"
    >
      <div className="space-y-6">
        {/* Metrics Grid */}
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-4">
          {metrics.map((metric) => {
            const Icon = metric.icon;
            return (
              <Card key={metric.title}>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {metric.title}
                  </CardTitle>
                  <Icon className={`h-4 w-4 ${metric.color}`} />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{metric.value}</div>
                  <p className="text-xs text-muted-foreground">
                    {metric.description}
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {metric.trend}
                  </p>
                </CardContent>
              </Card>
            );
          })}
        </div>

        {/* Recent Incidents */}
        <Card>
          <CardHeader>
            <CardTitle>Recent Incidents</CardTitle>
            <CardDescription>
              Latest incidents from ServiceNow
            </CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="flex items-center justify-center py-8">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
              </div>
            ) : recentIncidents.length === 0 ? (
              <div className="text-center py-8 text-muted-foreground">
                No incidents found
              </div>
            ) : (
              <div className="space-y-4">
                {recentIncidents.map((incident: Incident) => (
                  <div
                    key={incident.sys_id}
                    className="flex items-center justify-between border-b pb-4 last:border-0"
                  >
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs text-muted-foreground">
                          {incident.number}
                        </span>
                        {getPriorityBadge(incident.priority)}
                        {getStateBadge(incident.state)}
                      </div>
                      <p className="mt-1 font-medium">{incident.short_description}</p>
                      <p className="text-sm text-muted-foreground">
                        Assigned to: {incident.assigned_to || "Unassigned"}
                      </p>
                    </div>
                    <div className="text-right text-sm text-muted-foreground">
                      <div>{new Date(incident.opened_at).toLocaleDateString()}</div>
                      <div className="text-xs">
                        {new Date(incident.opened_at).toLocaleTimeString()}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* AI Assistant Quick Actions */}
        <Card>
          <CardHeader>
            <CardTitle>AI Assistant</CardTitle>
            <CardDescription>
              Get intelligent help with your incidents
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              <button className="flex flex-col items-center justify-center rounded-lg border p-6 text-center transition-colors hover:bg-accent">
                <AlertCircle className="mb-2 h-8 w-8 text-primary" />
                <h3 className="font-semibold">Classify Incidents</h3>
                <p className="text-xs text-muted-foreground">
                  Auto-categorize new incidents
                </p>
              </button>
              <button className="flex flex-col items-center justify-center rounded-lg border p-6 text-center transition-colors hover:bg-accent">
                <CheckCircle className="mb-2 h-8 w-8 text-primary" />
                <h3 className="font-semibold">Suggest Solutions</h3>
                <p className="text-xs text-muted-foreground">
                  Get AI-powered recommendations
                </p>
              </button>
              <button className="flex flex-col items-center justify-center rounded-lg border p-6 text-center transition-colors hover:bg-accent">
                <TrendingUp className="mb-2 h-8 w-8 text-primary" />
                <h3 className="font-semibold">Generate Summary</h3>
                <p className="text-xs text-muted-foreground">
                  Create incident reports
                </p>
              </button>
            </div>
          </CardContent>
        </Card>
      </div>
    </MainLayout>
  );
}
