package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Trajectory {

    private int degree;
    private List<Double> knotVector = new ArrayList<>();
    private List<ControlPoint> controlPoints = new ArrayList<>();

    public Trajectory() {}

    public int getDegree() { return degree; }
    public void setDegree(int degree) { this.degree = degree; }

    public List<Double> getKnotVector() { return knotVector; }
    public void setKnotVector(List<Double> knotVector) { this.knotVector = knotVector; }

    public List<ControlPoint> getControlPoints() { return controlPoints; }
    public void setControlPoints(List<ControlPoint> controlPoints) { this.controlPoints = controlPoints; }
}
