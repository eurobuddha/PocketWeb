
function showTitleOnAndroid(){
	if (window.navigator.userAgent.includes('Minima Browser')) {
		Android.showTitleBar();
	}
}

function jumpToHelp(){
	location.href="help.html?uid="+MDS.minidappuid;	
}

function jumpToHome(){
	location.href="index.html?uid="+MDS.minidappuid;	
}

function jumpToDev(){
	location.href="devmode.html?uid="+MDS.minidappuid;	
}

function jumpToMySite(){
	location.href="gensite.html?uid="+MDS.minidappuid;
}

function jumpToCatalog(){
	location.href="catalog.html?uid="+MDS.minidappuid;
}

function startupButtons(){
	$( "#button-help" ).button({
		icon: "ui-icon-help",
		showLabel: false
	}).click(function(){jumpToHelp();});
	
    $( "#button-home" ).button({
		icon: "ui-icon-home",
		showLabel: false
	}).click(function(){jumpToHome();});
    
    $( "#button-dev" ).button({
		icon: "ui-icon-edit",
		showLabel: false
	}).click(function(){jumpToDev();});
	
	$( "#button-mysite" ).button({
		icon: "ui-icon-vcard",
		showLabel: false
	}).click(function(){jumpToMySite();});

	$( "#button-catalog" ).button({
		icon: "ui-icon-grid",
		showLabel: false
	}).click(function(){jumpToCatalog();});
}

var NOTIFICATION_ENABLED = false;

function askNotificationPermission() {

	// Check if the browser supports notifications
	if (!("Notification" in window)) {
		console.log("This browser does not support notifications.");
		return;
	}
	
	Notification.requestPermission((result) => {
		if(result == "granted"){
			NOTIFICATION_ENABLED = true;		
		}
		console.log("Notifications : "+result);
	});
}

function showNotification(message){
	if(!NOTIFICATION_ENABLED){
		return;
	}

	const img = "browser.png";
	const txt = message;
	const notification = new Notification("MiniWEB", { body: txt, icon: img });
}

/* ═══════════════════════════════════════════════════
   THEME TOGGLE (light / dark)
   ═══════════════════════════════════════════════════ */
function applyTheme(theme) {
	if (theme === "light") {
		document.documentElement.setAttribute("data-theme", "light");
	} else {
		document.documentElement.removeAttribute("data-theme");
	}
	// Update toggle button icon if it exists
	var btn = document.getElementById("theme-toggle");
	if (btn) {
		btn.textContent = (theme === "light") ? "\u263E" : "\u2600";
		btn.title = (theme === "light") ? "Switch to dark mode" : "Switch to light mode";
	}
}

function toggleTheme() {
	var current = document.documentElement.getAttribute("data-theme");
	var next = (current === "light") ? "dark" : "light";
	applyTheme(next);

	// Persist via MDS.keypair if available, otherwise localStorage
	if (typeof MDS !== "undefined" && MDS.keypair) {
		MDS.keypair.set("pocketweb_theme", next, function(){});
	} else {
		try { localStorage.setItem("pocketweb_theme", next); } catch(e){}
	}
}

function loadTheme() {
	// Try MDS.keypair first, fall back to localStorage
	if (typeof MDS !== "undefined" && MDS.keypair) {
		MDS.keypair.get("pocketweb_theme", function(details) {
			if (details.status && details.value) {
				applyTheme(details.value);
			} else {
				// Check localStorage fallback
				try {
					var stored = localStorage.getItem("pocketweb_theme");
					if (stored) applyTheme(stored);
				} catch(e){}
			}
		});
	} else {
		try {
			var stored = localStorage.getItem("pocketweb_theme");
			if (stored) applyTheme(stored);
		} catch(e){}
	}
}
