package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Factsheet {

    private int headerId;
    private String timestamp;
    private String version;
    private String manufacturer;
    private String serialNumber;
    private TypeSpecification typeSpecification;
    private PhysicalParameters physicalParameters;

    public Factsheet() {}

    public int getHeaderId() { return headerId; }
    public void setHeaderId(int headerId) { this.headerId = headerId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public TypeSpecification getTypeSpecification() { return typeSpecification; }
    public void setTypeSpecification(TypeSpecification typeSpecification) { this.typeSpecification = typeSpecification; }

    public PhysicalParameters getPhysicalParameters() { return physicalParameters; }
    public void setPhysicalParameters(PhysicalParameters physicalParameters) { this.physicalParameters = physicalParameters; }
}
