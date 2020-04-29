package com.mcplusa.coveo.connector.aem.indexing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.EqualsAndHashCode;

@Getter
@Setter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Permission {

    public enum PERMISSION_TYPE {
        ALLOW, DENY
    }

    private String principalName;
    private PERMISSION_TYPE type;
    private boolean isGroup;
}
