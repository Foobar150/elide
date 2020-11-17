/*
 * Copyright 2020, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.contrib.dynamicconfighelpers.jsonformats;

import com.github.fge.jackson.NodeType;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.format.AbstractFormatAttribute;
import com.github.fge.jsonschema.format.FormatAttribute;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.msgsimple.bundle.MessageBundle;

public class ElideCardinalityFormatAttr extends AbstractFormatAttribute {
    private static final FormatAttribute INSTANCE = new ElideCardinalityFormatAttr();
    private static final String CARDINALITY_REGEX = "^(?i)(Tiny|Small|Medium|Large|Huge)$";

    public static final String FORMAT_NAME = "elideCardiality";
    public static final String TYPE_KEY = "elideCardiality.error.enum";
    public static final String TYPE_MSG = "Cardinality type [%s] is not allowed. Supported value is one of "
                    + "[Tiny, Small, Medium, Large, Huge].";

    private ElideCardinalityFormatAttr() {
        super(FORMAT_NAME, NodeType.STRING);
    }

    public static FormatAttribute getInstance() {
        return INSTANCE;
    }

    @Override
    public void validate(final ProcessingReport report, final MessageBundle bundle, final FullData data)
                    throws ProcessingException {
        final String input = data.getInstance().getNode().textValue();

        if (!input.matches(CARDINALITY_REGEX)) {
            report.error(newMsg(data, bundle, TYPE_KEY).putArgument("value", input));
        }
    }
}
