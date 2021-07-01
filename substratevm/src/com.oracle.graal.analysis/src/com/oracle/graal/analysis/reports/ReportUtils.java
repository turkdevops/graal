package com.oracle.graal.analysis.reports;

import com.oracle.graal.analysis.domain.AnalysisMethod;

public class ReportUtils {

    public static String parsingContext(AnalysisMethod method) {
        return com.oracle.graal.analysis.reports.ReportUtils.parsingContext(method, "   ");
    }

    public static String parsingContext(AnalysisMethod method, String indent) {
        StringBuilder msg = new StringBuilder();
        if (method.getTypeFlow().getParsingContext().length > 0) {
            // todo fix
            for (StackTraceElement e : method.getTypeFlow().getParsingContext()) {
                msg.append(String.format("%n%sat %s", indent, e));
            }
            msg.append(String.format("%n"));
        } else {
            msg.append(String.format(" <no parsing context available> %n"));
        }
        return msg.toString();
    }
}
