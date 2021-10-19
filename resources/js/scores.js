const buttons = document.querySelectorAll('.btn')
buttons.forEach(function(currentBtn){
  currentBtn.addEventListener('click', setHidey)
})

// accepts button and shows/hides ul in same tr as that button
function setHidey() {
    const h = 'hidey';
    var ulToChangeList = this.parentElement.parentElement.getElementsByTagName('ul');
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