package com.minidb.executor.planner.logical;

import com.minidb.sql.ast.SelectItem;

import java.util.List;

public class ProjectionNode implements LogicalPlan {

    private final LogicalPlan input;
    private final List<SelectItem> items;

    public ProjectionNode(LogicalPlan input, List<SelectItem> items) {
        this.input = input;
        this.items = items;
    }

    public LogicalPlan getInput() {
        return input;
    }

    public List<SelectItem> getItems() {
        return items;
    }
}