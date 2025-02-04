document.getElementById('search-button').addEventListener('click', function() {
    // Get the search term
    const searchTerm = document.getElementById('search-input').value.trim().toLowerCase();

    // Perform search (example implementation)
    const resultsContainer = document.getElementById('search-results');
    const searchItems = document.querySelectorAll('.subject-card'); // Assuming these are the items you're searching

    // Clear previous results
    resultsContainer.innerHTML = '';

    // Filter items based on search term and display results
    const filteredItems = Array.from(searchItems).filter(item => {
        return item.textContent.toLowerCase().includes(searchTerm);
    });

    // Append filtered items to results container
    filteredItems.forEach(item => {
        const clone = item.cloneNode(true); // Clone the subject card
        clone.classList.add('search-result-item');
        resultsContainer.appendChild(clone);
    });

    // Show navigation buttons if there are results
    const navigationContainer = document.getElementById('search-navigation');
    if (filteredItems.length > 0) {
        navigationContainer.style.display = 'flex';
        let currentIndex = 0;

        // Scroll to the first result
        const resultItems = document.querySelectorAll('.search-result-item');
        resultItems[currentIndex].classList.add('active');
        resultItems[currentIndex].scrollIntoView({ behavior: 'smooth', block: 'center' });

        // Next button event
        document.getElementById('next-search').addEventListener('click', function() {
            if (currentIndex < resultItems.length - 1) {
                resultItems[currentIndex].classList.remove('active');
                currentIndex++;
                resultItems[currentIndex].classList.add('active');
                resultItems[currentIndex].scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        });

        // Previous button event
        document.getElementById('prev-search').addEventListener('click', function() {
            if (currentIndex > 0) {
                resultItems[currentIndex].classList.remove('active');
                currentIndex--;
                resultItems[currentIndex].classList.add('active');
                resultItems[currentIndex].scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        });
    } else {
        navigationContainer.style.display = 'none';
    }
});
