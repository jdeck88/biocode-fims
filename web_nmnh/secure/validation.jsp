<%@ include file="../header-home.jsp" %>

<div id="validation" class="section">
    <div id="warning"></div>

    <div class="sectioncontent">

        <h2>Validation</h2>

        <c:if test="${param.error != null}">
        <script>
        $(document).ready(function(){
            $("#dialogContainer").addClass("error");
            dialog("Authentication Error!<br><br>" + ${param.error}, "Error", {"OK": function() {
                $("#dialogContainer").removeClass("error");
                $(this).dialog("close"); }
            });
        });</script>
        </c:if>

        <form method="POST">
            <table class="table" style="width:600px">
                <tr>
                    <td align="right">&nbsp;&nbsp;FIMS Spreadsheet&nbsp;&nbsp;</td>
                    <td><input type="file" class="btn btn-default btn-xs" name="dataset" id="dataset" /></td>
                </tr>


                <tr class="toggle-content" id="projects_toggle">
                    <td align="right">Project&nbsp;&nbsp;</td>
                    <td>
                        <select width=20 name="project_id" id="projects">
                            <option value=0>Loading projects ...</option>
                        </select>
                    </td>
                </tr>

                <tr>
                    <td align="right">Upload&nbsp;&nbsp;</td>
                    <td style="font-size:11px;">
                        <c:if test="${user == null}">
                            <input type="checkbox" id="upload" disabled="disabled" /> (login to upload)
                        </c:if>
                        <c:if test="${user != null}">
                            <input type="checkbox" id="upload" name="upload" />
                        </c:if>
                    </td>
                </tr>

                <tr>
                    <td align="right">Final Copy&nbsp;&nbsp;</td>
                    <td style="font-size:11px;">
                            <input type="checkbox" id="finalCopy" name="final_copy" />
                    </td>
                </tr>

                <tr class="toggle-content" id="expedition_code_toggle">
                    <td align="right">Dataset Code&nbsp;&nbsp;</td>
                    <td id="expedition_code_container"><input type="text" name="expedition_code" id="expedition_code" /></td>
                </tr>

                <tr>
                    <td></td>
                    <td><input type="button" value="Submit" class="btn btn-default btn-xs"></td>
                </tr>
            </table>
        </form>

        <div id=resultsContainer style='overflow:auto; display:none;'>
        </div>

    </div>
</div>

<script>
    $(document).ready(function() {
        fimsBrowserCheck($('#warning'));
        validationFormToggle();
        populateProjects();
        // call validatorSubmit if the enter key was pressed in an input
        $("input").keydown( function(event) {
            if (event.which == 13) {
            event.preventDefault();
            validatorSubmit();
            }
        });
        $("input[type=button]").click(function() {
            validatorSubmit();
        });

        // expand/contract messages -- use 'on' function and initially to 'body' since this is dynamically loaded
        jQuery("body").on("click", "#groupMessage", function () {
            $(this).parent().siblings("dd").slideToggle();
        });
    });
</script>

<%@ include file="../footer.jsp" %>
