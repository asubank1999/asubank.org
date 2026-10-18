// Toggle dark mode and save preference to localStorage
document.getElementById('mode-toggle').addEventListener('click', () => {
    document.body.classList.toggle('dark-mode');
    localStorage.setItem('theme', document.body.classList.contains('dark-mode') ? 'dark' : 'light');
});

// Apply dark mode on page load if preferred
window.addEventListener('DOMContentLoaded', () => {
    if (localStorage.getItem('theme') === 'dark' || (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
        document.body.classList.add('dark-mode');
    }
});

// Copy text to clipboard function
function copyText() {
    const copyText = document.getElementById("textToCopy");
    copyText.select();
    copyText.setSelectionRange(0, 99999);
    navigator.clipboard.writeText(copyText.value).then(() => {
        document.getElementById("message").textContent = "You have my CliQ number! Any amount is appreciated - Name starts with 'qusai'";
    }).catch(err => {
        console.error('Could not copy text: ', err);
        document.getElementById("message").textContent = "Failed to copy text.";
    });
}

// Handle link clicks with InAppBrowser when device is ready
document.addEventListener("deviceready", function() {
    document.querySelectorAll("a").forEach(link => {
        link.addEventListener("click", function(event) {
            event.preventDefault(); // Prevent default link behavior
            const url = this.href; // Get the link's href

            // Use InAppBrowser to open external URLs
            if (url.startsWith("http")) {
                cordova.InAppBrowser.open(url, "_blank", "location=yes"); // "_system" to open in the default browser
            } else {
                // Handle internal navigation if needed
                window.location.href = url; // This allows internal navigation
            }
        });
    });
});
