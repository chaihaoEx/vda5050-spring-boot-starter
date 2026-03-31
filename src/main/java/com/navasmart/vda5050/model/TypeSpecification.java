package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TypeSpecification {

    private String seriesName;
    private String seriesDescription;
    private String agvKinematics;
    private String agvClass;
    private Double maxLoadMass;
    private List<String> localizationTypes = new ArrayList<>();
    private List<String> navigationTypes = new ArrayList<>();

    public TypeSpecification() {}

    public String getSeriesName() { return seriesName; }
    public void setSeriesName(String seriesName) { this.seriesName = seriesName; }

    public String getSeriesDescription() { return seriesDescription; }
    public void setSeriesDescription(String seriesDescription) { this.seriesDescription = seriesDescription; }

    public String getAgvKinematics() { return agvKinematics; }
    public void setAgvKinematics(String agvKinematics) { this.agvKinematics = agvKinematics; }

    public String getAgvClass() { return agvClass; }
    public void setAgvClass(String agvClass) { this.agvClass = agvClass; }

    public Double getMaxLoadMass() { return maxLoadMass; }
    public void setMaxLoadMass(Double maxLoadMass) { this.maxLoadMass = maxLoadMass; }

    public List<String> getLocalizationTypes() { return localizationTypes; }
    public void setLocalizationTypes(List<String> localizationTypes) { this.localizationTypes = localizationTypes; }

    public List<String> getNavigationTypes() { return navigationTypes; }
    public void setNavigationTypes(List<String> navigationTypes) { this.navigationTypes = navigationTypes; }
}
