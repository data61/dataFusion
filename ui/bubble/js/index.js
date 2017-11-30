let colourMap = {
  "ORGANIZATION": "#880E4F",
  "PERSON": "#BF360C",
  "FROM": "#311B92",
  "TO": "#311B92",
  "BCC": "#311B92"
}

let size = Math.min(window.innerHeight, window.innerWidth)
let width = 500
let height = 500

function displayDataSidebar (data) {
  let info = d3.select("#info")
  let title = document.querySelector("#name")
  let desc = document.querySelector("#desc")

  info.style("visibility", "visible")
  title.innerHTML = data.title
  desc.innerHTML = data.desc
}

function hideDataSidebar () {
  let info = d3.select("#info")
  info.style("visibility", "hidden")
}

// Form switcher
(function () {
  function toggleClosedClass (el, forceClose) {
    let cn = el.parentNode.className
    let closedIdx = cn.indexOf(" closed")
    if (closedIdx > -1 && !forceClose) {
      cn = cn.slice(0, closedIdx)
    } else {
      cn = cn + " closed"
    }
    el.parentNode.className = cn
  }

  let clickers = Array.prototype.slice.call(document.querySelectorAll(".graph-form h2"))

  clickers.forEach((clicker, idx, arr) => {
    clicker.onclick = function() {
      return function (el, idx) {
        let others = clickers.filter((el, currIdx, arr) => idx !== currIdx)
        others.forEach(clicker => toggleClosedClass(clicker, true))
        toggleClosedClass(el, false)
      }(this, idx)
    }
  })
})()

// Form actions
document.querySelector("#fetchForm").onsubmit = evt => {
  evt.preventDefault()
  getGraph()
}

document.querySelector("#optsForm").onsubmit = evt => {
  evt.preventDefault()
  drawGraph()
}
