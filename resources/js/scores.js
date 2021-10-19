// accepts button and shows/hides ul in same tr as that button
function setHidey(but) {
    const h = 'hidey';
    var ulToChangeList = but.parentElement.parentElement.getElementsByTagName('ul');
    for (var i = 0; i < ulToChangeList.length; i++) {
        var el = ulToChangeList[i];
        if (el.classList.contains(h)) {
            el.classList.remove(h);
        }
        else {
            el.classList.add(h);
        }
    }
}