package edu.hm.hafner.analysis.parser;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.hm.hafner.analysis.Issue;
import edu.hm.hafner.analysis.IssueBuilder;
import edu.hm.hafner.analysis.Report;
import edu.hm.hafner.analysis.Severity;

import static j2html.TagCreator.*;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.commons.lang3.StringUtils.endsWith;

/**
 * Parser for Veracode Pipeline Scanner (pipeline-scan) tool.
 *
 * @author Juri Duval
 */
public class VeraCodePipelineScannerParser extends JsonIssueParser {
    private static final String VALUE_NOT_SET = "-";
    private static final int VERACODE_LOW_THRESHOLD = 2;
    private static final int VERACODE_HIGH_THRESHOLD = 4;
    private static final long serialVersionUID = 1L;

    @Override
    protected void parseJsonObject(final Report report, final JSONObject jsonReport, final IssueBuilder issueBuilder) {
        JSONArray findings = jsonReport.optJSONArray("findings");
        if (findings != null) {
            parseFindings(report, findings, issueBuilder);
        }
    }

    private void parseFindings(final Report report, final JSONArray resources, final IssueBuilder issueBuilder) {
        for (int i = 0; i < resources.length(); i++) {
            final Object item = resources.get(i);
            if (item instanceof JSONObject) {
                final JSONObject finding = (JSONObject) item;
                report.add(convertToIssue(finding, issueBuilder));
            }
        }
    }

    private Issue convertToIssue(final JSONObject finding, final IssueBuilder issueBuilder) {
        final String rawFileName = getSourceFileField(finding, "file", VALUE_NOT_SET);
        final String enrichedFileName = getEnrichedFileName(rawFileName);
        final int line = getSourceFileField(finding, "line", 0);
        final int severity = finding.getInt("severity");
        final String title = finding.getString("title");
        final String issueType = finding.getString("issue_type");
        final String issueTypeId = finding.getString("issue_type_id");
        final String scope = getSourceFileField(finding, "scope", VALUE_NOT_SET);
        final String packageName = getPackageName(scope);
        return issueBuilder
                .setFileName(enrichedFileName)
                .setLineStart(line)
                .setSeverity(mapSeverity(severity))
                .setMessage(issueType)
                .setPackageName(packageName)
                .setType(title)
                .setCategory(issueTypeId)
                .setDescription(formatDescription(enrichedFileName, finding))
                .buildAndClean();
    }

    /**
     * Prepends .java files with src/main/java so that they can be correctly linked to source code files in Jenkins UI.
     * Otherwise, files are returned as is.
     * The solution does not cater for all scenarios but should be sufficient for most common use case (Java/Kotlin project
     * with Maven folder structure).
     *
     * @param rawFileName
     *         file name reported by Veracode
     *
     * @return original file name or a java file name prepended with src/main/java
     */
    private String getEnrichedFileName(final String rawFileName) {
        if (endsWith(rawFileName, ".java") && !startsWith(rawFileName, "src/main/java/")) {
            return "src/main/java/" + rawFileName;
        }
        else if (endsWith(rawFileName, ".kt") && !startsWith(rawFileName, "src/main/kotlin/")) {
            return "src/main/kotlin/" + rawFileName;
        }
        else {
            return rawFileName;
        }
    }

    private String getPackageName(final String scope) {
        if (scope.contains(".")) {
            return StringUtils.substringBeforeLast(scope, ".");
        }
        else {
            return VALUE_NOT_SET;
        }
    }

    /**
     * Retrieve source file values in a null safe manner.
     * Veracode has nested json objects representing a source file ( files -> source_file -> values) for which we need
     * to do null checking.
     *
     * @param finding
     *         contains top level vulnerability information.
     * @param key
     *         used to retrieve values from nested source file.
     * @param altValue
     *         in case there is a missing source file.
     * @param <T>
     *         type of the return value.
     *
     * @return field value of the source file.
     */
    private <T> T getSourceFileField(final JSONObject finding, final String key, final T altValue) {
        final JSONObject files = finding.optJSONObject("files");
        if (files != null) {
            final JSONObject sourceFile = files.optJSONObject("source_file");
            if (sourceFile != null) {
                return (T) sourceFile.get(key);
            }
        }

        return altValue;
    }

    /**
     * Map veracode severity to analysis-model severity.
     * See <a href="https://docs.veracode.com/r/review_severity_exploitability">Veracode severity table</a> for details
     * on scoring.
     *
     * @param severity
     *         as an integer from 0 to 5 (inclusive).
     *
     * @return {@link Severity}
     */
    private Severity mapSeverity(final int severity) {
        if (severity <= VERACODE_LOW_THRESHOLD) {
            return Severity.WARNING_LOW;
        }
        else if (severity >= VERACODE_HIGH_THRESHOLD) {
            return Severity.WARNING_HIGH;
        }
        else {
            return Severity.WARNING_NORMAL;
        }

    }

    private String formatDescription(final String fileName, final JSONObject finding) {
        final String cweId = finding.optString("cwe_id", VALUE_NOT_SET);
        final String flawLink = finding.optString("flaw_details_link", VALUE_NOT_SET);
        final String severity = finding.optString("severity", VALUE_NOT_SET);
        final String displayHtml = finding.optString("display_text", VALUE_NOT_SET);
        return join(div(b("Resource: "), text(fileName)),
                div(b("CWE Id: "), text(cweId)),
                div(b("Flaw Details: "), text(flawLink)),
                div(b("Severity: "), text(severity)),
                p(rawHtml(displayHtml))).render();
    }

}
