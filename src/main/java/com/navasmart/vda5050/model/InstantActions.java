package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * VDA5050 即时动作消息（InstantActions）。
 * <p>
 * 由调度系统通过 MQTT 发送给 AGV，用于下发需要立即执行的动作，
 * 与当前订单无关，AGV 收到后应立即执行。
 * <p>
 * MQTT 主题方向：主控 -> AGV
 * <p>
 * 主题格式：{@code <interfaceName>/<majorVersion>/<manufacturer>/<serialNumber>/instantActions}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstantActions {

    /** 消息头序号，每次发送递增，用于消息排序和去重。 */
    private int headerId;

    /** 消息时间戳，ISO 8601 格式。 */
    private String timestamp;

    /** VDA5050 协议版本号。 */
    private String version;

    /** AGV 制造商名称。 */
    private String manufacturer;

    /** AGV 唯一序列号。 */
    private String serialNumber;

    /**
     * 即时动作列表（JSON 键名 {@code actions}，见 VDA5050 §7.4）。
     */
    private List<Action> actions = new ArrayList<>();

    public InstantActions() {}

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

    public List<Action> getActions() { return actions; }
    public void setActions(List<Action> actions) {
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }
}
