// /Users/tuanpham/Chinese-chess-nework-programming/src/Front_end/components/cc-link.js
class CCLink extends HTMLElement{
	static get observedAttributes(){ return ['href','text']; }
	constructor(){
		super();
		const root = this.attachShadow({mode:'open'});
		root.innerHTML = `
			<style>
				:host{
					display:block;
					margin-top: calc(var(--mt, 12px) * var(--scale));
				}
				a{
					color: #dc3545;
					text-decoration: none;
					font-size: calc(18px * var(--scale));
					font-weight: 500;
					opacity: 1;
					transition: opacity .2s ease, color .2s ease;
				}
				a:hover{ 
					opacity: 1;
					color: #c82333;
				}
			</style>
			<a part="link" href="#"></a>
		`;
		this.$link = root.querySelector('a');
	}
	connectedCallback(){ 
		this.#sync();
		this.$link.addEventListener('click', this.#handleClick);
	}
	disconnectedCallback(){
		this.$link.removeEventListener('click', this.#handleClick);
	}
	attributeChangedCallback(){ this.#sync(); }
	#sync(){
		const text = this.getAttribute('text') || '';
		const href = this.getAttribute('href') || '#';
		this.$link.textContent = text;
		this.$link.href = href;
	}
	#handleClick = (e) => {
		e.preventDefault();
		this.dispatchEvent(new CustomEvent('cc-link-click', {
			bubbles: true,
			detail: { text: this.getAttribute('text') || '' }
		}));
	}
}
customElements.define('cc-link', CCLink);