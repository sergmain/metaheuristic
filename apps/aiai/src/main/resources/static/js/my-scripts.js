function focusLoginInput() {
    var textbox = document.getElementById("j_username");
    textbox.focus();
}

function my_confirm(message, title, actionName, okAction) {
    let $dialog = $( "#dialog-confirm" );
    // TODO need to check in another way
    if ($dialog===undefined) {
        console.log("Element with id='dialog-confirm' wasn't found");
        return;
    }
    $dialog.attr('title', title);
    let $dialogConfirmation = $( "#dialog-confirmation-text" );
    // TODO need to check in another way
    if ($dialogConfirmation===undefined) {
        console.log("Element with id='dialog-confirmation-text' wasn't found");
        return;
    }
    $dialogConfirmation.empty().append(message);
    $dialog.show();
    // TODO need to check in another way
    $dialog.dialog({
        resizable: false,
        height: "auto",
        width: 400,
        modal: true,
        buttons: {
            Confirm: function() {
                $( this ).dialog( "close" );
                okAction();
            },
            Cancel: function() {
                $( this ).dialog( "close" );
            }
        }
    });
}

function my_alert(message, title) {
    let $dialog = $( "#dialog-message" );
    $dialog.attr('title', title);
    $( "#dialog-message-text" ).empty().append(message);
    $dialog.show();
    $dialog.dialog({
        modal: true,
        buttons: {
            Ok: function() {
                $( this ).dialog( "close" );
            }
        }
    });

}

