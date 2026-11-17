'use strict';
// The restaurant's food and drinks menu, shown on the homepage.
// It is a printed-menu style listing only: there is no online ordering, cart or checkout.
// Dish photos live in frontend/assets/menu/ (an image name without an extension falls back to the .svg illustration).
const MENU = [
  { id: 'filipino', title: 'Filipino', intro: 'Homestyle dishes from Luzon, Visayas and Mindanao.', items: [
    { name: 'Chicken Adobo', price: 320, image: 'chicken-adobo.webp', alt: 'Bowl of chicken adobo drumsticks in soy-vinegar sauce with bay leaves, garlic and peppercorns, with steamed rice on the side',
      description: 'Chicken drumsticks braised in soy sauce, cane vinegar, garlic, bay leaf and peppercorns, served with steamed rice.' },
    { name: 'Sinigang na Baboy', price: 380, image: 'sinigang-na-baboy.webp', alt: 'Bowl of pork sinigang with kangkong, radish, tomato, okra, string beans and green chili',
      description: 'Pork belly in a sour tamarind broth with kangkong, radish, tomato, okra, string beans and green chili.' },
    { name: 'Kare-Kare', price: 450, image: 'kare-kare.webp', alt: 'Wooden bowl of kare-kare in peanut sauce with bok choy, string beans and eggplant, with rice and shrimp paste on the side',
      description: 'Crispy pork belly with bok choy and string beans in a rich peanut sauce, with steamed rice and sautéed shrimp paste on the side. Contains peanuts and shellfish.' },
    { name: 'Lechon Kawali', price: 390, image: 'lechon-kawali.webp', alt: 'Crispy pork belly pieces on a banana leaf with a bowl of liver sauce and spiced vinegar dip',
      description: 'Crispy deep-fried pork belly served on banana leaf with house liver sauce and spiced vinegar.' },
  ] },
  { id: 'chinese', title: 'Chinese', intro: 'Dim sum, wok-fried rice and noodle favorites.', items: [
    { name: 'Pork Siomai', price: 160, image: 'pork-siomai.webp', alt: 'Pork siomai dumplings in a bamboo steamer with a dish of soy sauce and chopsticks',
      description: 'Seven steamed pork and shrimp dumplings in a bamboo steamer, with soy sauce for dipping. Contains shellfish.' },
    { name: 'Yang Chow Fried Rice', price: 240, image: 'yang-chow-fried-rice.webp', alt: 'Platter of fried rice with shrimp, sliced Chinese sausage, peas and carrots, garnished with parsley and carved carrot',
      description: 'Wok-fried rice with shrimp, sliced Chinese sausage, egg, peas and carrots, served on a platter with chili sauce. Good for two. Contains shellfish and egg.' },
    { name: 'Sweet and Sour Pork', price: 340, image: 'sweet-and-sour-pork.webp', alt: 'Bowl of glazed sweet and sour pork with green bell pepper, pineapple and onion, topped with sesame seeds, with steamed rice on the side',
      description: 'Crisp pork tossed in a tangy sweet and sour glaze with green bell pepper, pineapple and onion, sprinkled with sesame seeds and served with steamed rice. Contains sesame.' },
    { name: 'Beef Wonton Noodle Soup', price: 280, image: 'beef-wonton-noodle-soup.webp', alt: 'Bowl of egg noodle soup with braised beef, wontons and napa cabbage, topped with scallions and fried garlic, with calamansi on the side',
      description: 'Egg noodles in clear beef broth with braised beef, pork wontons and napa cabbage, topped with scallions and fried garlic, with calamansi on the side. Contains egg and wheat.' },
  ] },
  { id: 'japanese', title: 'Japanese', intro: 'Ramen, fresh cuts and crisp fried plates.', items: [
    { name: 'Tonkotsu Ramen', price: 390, image: 'tonkotsu-ramen.png', alt: 'Bowl of tonkotsu ramen with sliced chashu pork, two marinated soft-boiled egg halves, scallions and black garlic oil',
      description: 'Creamy pork bone broth with noodles, sliced chashu pork, marinated soft-boiled egg, scallions and a drizzle of black garlic oil. Contains egg and wheat.' },
    { name: 'Salmon Sashimi', price: 420, image: 'salmon-sashimi.webp', alt: 'Slices of salmon sashimi on a stoneware plate with shiso leaves, shredded daikon radish and wasabi, with soy sauce behind',
      description: 'Six slices of fresh salmon with shiso leaves, shredded daikon radish, wasabi and soy sauce. Served raw.' },
    { name: 'Chicken Katsu Curry', price: 360, image: 'chicken-katsu-curry.webp', alt: 'Sliced panko-breaded chicken cutlet in golden curry sauce beside a mound of rice topped with black sesame and a side salad',
      description: 'Panko-breaded chicken cutlet in mild Japanese curry sauce, with a mound of seasoned rice topped with black sesame and a small radish and greens salad. Contains wheat, egg and sesame.' },
    { name: 'Ebi Tempura', price: 380, image: 'ebi-tempura.png', alt: 'Three crispy panko-coated prawns on a slate plate with tartar sauce, lemon wedge, broccoli and cherry tomato',
      description: 'Three crispy panko-coated prawns over tartar sauce, with shredded cabbage, broccoli, cherry tomato and a lemon wedge. Contains shellfish, wheat and egg.' },
  ] },
  { id: 'drinks', title: 'Drinks', intro: 'Something cold to go with your meal.', items: [
    { name: 'Calamansi Juice', price: 90, image: 'calamansi-juice.webp', alt: 'Cut-glass tumbler of iced calamansi juice with a whole calamansi and a citrus slice, with fresh calamansi in front',
      description: 'Freshly squeezed calamansi, lightly sweetened and served over ice with a whole calamansi and a citrus slice.' },
    { name: 'Sago’t Gulaman', price: 85, image: 'sago-at-gulaman.png', alt: 'Two tall glasses of sago’t gulaman layered with tapioca pearls, dark and red gulaman jelly and shaved ice in brown sugar syrup',
      description: 'Brown sugar syrup drink over shaved ice, layered with tapioca pearls and dark and red gulaman jelly cubes.' },
    { name: 'Iced Jasmine Tea', price: 80, image: 'iced-jasmine-tea.webp', alt: 'Tall glass of iced jasmine tea garnished with a jasmine flower, with a sprig of jasmine blossoms beside it',
      description: 'Brewed jasmine tea served over ice with a jasmine flower garnish, unsweetened or with syrup on request.' },
    { name: 'Matcha Latte', price: 150, image: 'matcha-latte.webp', alt: 'Hot matcha latte with leaf latte art in a speckled ceramic cup on a wooden tray, with a spoonful of matcha powder',
      description: 'Japanese green tea whisked with steamed milk and finished with latte art, served hot or iced. Contains milk.' },
  ] },
];
const menuPrice = value => new Intl.NumberFormat('en-PH', { style: 'currency', currency: 'PHP', maximumFractionDigits: 0 }).format(value);
/** Returns the menu section markup. Every dish is a list item with an image, name, description and price. */
function menuSection() {
  const group = g => `<section class="menu-group" id="menu-${g.id}" aria-labelledby="menu-${g.id}-title" tabindex="-1"><div class="menu-group-head"><h3 id="menu-${g.id}-title">${g.title}</h3><p>${g.intro}</p></div><ul class="menu-grid">${g.items.map(item =>
    `<li class="menu-item"><img src="/assets/menu/${item.image.includes('.') ? item.image : `${item.image}.svg`}" alt="${item.alt}" width="400" height="300" loading="lazy" decoding="async"><div class="menu-item-body"><div class="menu-item-title"><h4>${item.name}</h4><span class="menu-price"><span class="visually-hidden">Price: </span>${menuPrice(item.price)}</span></div><p>${item.description}</p></div></li>`).join('')}</ul></section>`;
  return `<section id="menu" class="menu-section" aria-labelledby="menu-title"><div class="eyebrow">Our menu</div><h2 id="menu-title">Three kitchens. One table.</h2><p class="menu-lead">Filipino, Chinese and Japanese dishes, cooked to order and served at your table.</p><nav class="menu-nav" aria-label="Menu categories">${MENU.map(g => `<button type="button" class="button secondary small" data-menu-jump="menu-${g.id}">${g.title}</button>`).join('')}</nav>${MENU.map(group).join('')}<p class="menu-note">Prices are in Philippine pesos (₱) per serving and include VAT. The menu is served in the restaurant: order with our staff at your table. Online food ordering is not available. Please tell us about allergies when you reserve. Photos are for presentation; actual plating may differ.</p></section>`;
}
/** Category buttons move focus to their group without changing the page address. */
function bindMenu(root) {
  root.querySelectorAll('[data-menu-jump]').forEach(button => button.addEventListener('click', () => {
    const target = document.getElementById(button.dataset.menuJump);
    if (target) { target.scrollIntoView({ behavior: 'smooth', block: 'start' }); target.focus({ preventScroll: true }); }
  }));
}
