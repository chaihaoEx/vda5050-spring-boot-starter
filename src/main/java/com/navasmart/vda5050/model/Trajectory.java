package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * VDA5050 NURBS 轨迹定义（Trajectory）。
 * <p>
 * 使用非均匀有理 B 样条（NURBS）曲线描述 AGV 在边上的精确行驶轨迹。
 * 轨迹由阶数（degree）、节点向量（knotVector）和控制点（controlPoints）三部分组成。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Trajectory {

    /** NURBS 曲线的阶数。定义曲线的平滑程度，取值通常为 1（线性）、2（二次）或 3（三次）。 */
    private int degree;

    /** NURBS 节点向量，定义控制点对曲线的影响范围。长度 = 控制点数 + degree + 1。 */
    private List<Double> knotVector = new ArrayList<>();

    /** NURBS 控制点列表，定义曲线的形状。 */
    private List<ControlPoint> controlPoints = new ArrayList<>();

    public Trajectory() {}

    public int getDegree() { return degree; }
    public void setDegree(int degree) { this.degree = degree; }

    public List<Double> getKnotVector() { return knotVector; }
    public void setKnotVector(List<Double> knotVector) { this.knotVector = knotVector; }

    public List<ControlPoint> getControlPoints() { return controlPoints; }
    public void setControlPoints(List<ControlPoint> controlPoints) { this.controlPoints = controlPoints; }
}
