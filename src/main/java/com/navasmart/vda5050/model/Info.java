package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * VDA5050 信息条目。
 * <p>
 * 描述 AGV 上报的通知信息，包括信息类型、描述、级别和关联引用。
 * 与 {@link Error} 类似，但用于非错误类的通知场景。
 * 该对象作为 AGV 状态消息的一部分上报给主控系统。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Info {

    /**
     * 信息类型标识符。
     * <p>
     * 用于标识信息的具体类型，便于主控系统进行分类处理。
     * </p>
     */
    private String infoType;

    /**
     * 信息引用列表。
     * <p>
     * 包含与该信息相关的键值对引用，用于提供更多的上下文。
     * </p>
     */
    private List<InfoReference> infoReferences = new ArrayList<>();

    /**
     * 信息的可读描述。可选字段。
     */
    private String infoDescription;

    /**
     * 信息级别。
     * <p>
     * 可选值：
     * <ul>
     *   <li>{@code "INFO"} - 普通信息</li>
     *   <li>{@code "DEBUG"} - 调试信息</li>
     * </ul>
     * </p>
     */
    private String infoLevel;

    public Info() {}

    public String getInfoType() { return infoType; }
    public void setInfoType(String infoType) { this.infoType = infoType; }

    public List<InfoReference> getInfoReferences() { return infoReferences; }
    public void setInfoReferences(List<InfoReference> infoReferences) { this.infoReferences = infoReferences; }

    public String getInfoDescription() { return infoDescription; }
    public void setInfoDescription(String infoDescription) { this.infoDescription = infoDescription; }

    public String getInfoLevel() { return infoLevel; }
    public void setInfoLevel(String infoLevel) { this.infoLevel = infoLevel; }
}
